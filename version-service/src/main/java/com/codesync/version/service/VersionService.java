package com.codesync.version.service;

import com.codesync.version.dto.*;
import com.codesync.version.exception.DuplicateResourceException;
import com.codesync.version.exception.InvalidVersionOperationException;
import com.codesync.version.exception.ResourceNotFoundException;
import com.codesync.version.model.*;
import com.codesync.version.repository.BranchRepository;
import com.codesync.version.repository.CommitFileRepository;
import com.codesync.version.repository.CommitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business logic for the Version Service.
 *
 * Implements a Git-inspired version control system:
 *  - Branches are named lines of development (e.g. "main", "feature/auth")
 *  - Commits are immutable snapshots of a set of files
 *  - Each commit has a SHA-256-derived hash and belongs to one branch
 *  - Diff compares file content between any two commits
 *
 * Design decisions:
 *  - File content is stored per-commit (not delta-compressed) for simplicity.
 *  - A "main" branch is auto-created when the first commit is made to a project.
 *  - The Version Service does NOT talk to the File Service directly;
 *    the caller (API Gateway / frontend) supplies file content in the commit payload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VersionService {

    private final BranchRepository     branchRepository;
    private final CommitRepository     commitRepository;
    private final CommitFileRepository commitFileRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // ── BRANCH OPERATIONS ───────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create a new branch in a project.
     * Branch names must be unique per project.
     */
    public BranchResponse createBranch(CreateBranchRequest req, Long userId) {
        log.info("User {} creating branch '{}' in project {}", userId, req.getName(), req.getProjectId());

        if (branchRepository.existsByProjectIdAndName(req.getProjectId(), req.getName())) {
            throw new DuplicateResourceException(
                    "Branch '" + req.getName() + "' already exists in project " + req.getProjectId());
        }

        boolean isFirstBranch = branchRepository.countByProjectId(req.getProjectId()) == 0;

        Branch branch = Branch.builder()
                .name(req.getName())
                .projectId(req.getProjectId())
                .createdBy(userId)
                .defaultBranch(isFirstBranch)   // first branch is default
                .build();

        branch = branchRepository.save(branch);
        log.info("Branch '{}' created with id={}", req.getName(), branch.getId());
        return toBranchResponse(branch);
    }

    /**
     * Auto-create the "main" branch the first time a project commits.
     * Called internally when no branches exist yet.
     */
    private Branch ensureMainBranch(Long projectId, Long userId) {
        return branchRepository.findByProjectIdAndDefaultBranchTrue(projectId)
                .orElseGet(() -> {
                    log.info("Auto-creating 'main' branch for project {}", projectId);
                    Branch main = Branch.builder()
                            .name("main")
                            .projectId(projectId)
                            .createdBy(userId)
                            .defaultBranch(true)
                            .build();
                    return branchRepository.save(main);
                });
    }

    /** List all branches for a project. */
    @Transactional(readOnly = true)
    public List<BranchResponse> listBranches(Long projectId) {
        return branchRepository.findByProjectId(projectId)
                .stream()
                .map(this::toBranchResponse)
                .collect(Collectors.toList());
    }

    /** Get a single branch by ID. */
    @Transactional(readOnly = true)
    public BranchResponse getBranch(Long branchId) {
        Branch branch = findBranchOrThrow(branchId);
        return toBranchResponse(branch);
    }

    @Transactional(readOnly = true)
    public String getLatestFileContent(
            Long branchId,
            Long fileId
    ) {

        List<Commit> commits =
                commitRepository
                .findByBranchIdOrderByCreatedAtDesc(
                        branchId,
                        Pageable.unpaged()
                )
                .getContent();

        for (Commit commit : commits) {

            Optional<CommitFile> file =
                    commit.getFiles()
                    .stream()
                    .filter(f ->
                            Objects.equals(
                                    f.getFileId(),
                                    fileId
                            )
                    )
                    .findFirst();

            if (file.isPresent()) {
                return file.get().getContent();
            }
        }

        return "";
    }
    
    /** Delete a branch. Cannot delete the default branch. */
    public void deleteBranch(Long branchId, Long userId) {
        Branch branch = findBranchOrThrow(branchId);

        if (branch.isDefaultBranch()) {
            throw new InvalidVersionOperationException(
                    "Cannot delete the default branch '" + branch.getName() + "'"
            );
        }

        // delete child commit files
        List<Commit> commits = commitRepository.findByBranchIdOrderByCreatedAtDesc(
                branchId,
                org.springframework.data.domain.Pageable.unpaged()
        ).getContent();

        for (Commit commit : commits) {
            commitFileRepository.deleteAll(commit.getFiles());
        }

        // delete commits
        commitRepository.deleteAll(commits);

        // delete branch
        branchRepository.delete(branch);

        log.info("Branch id={} '{}' deleted by user {}",
                branchId,
                branch.getName(),
                userId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── COMMIT OPERATIONS ───────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create a new commit on a branch.
     *
     * Steps:
     *  1. Resolve (or auto-create) the target branch.
     *  2. Generate a unique commit hash.
     *  3. Persist the commit + file snapshots.
     *  4. Advance the branch's headCommitId.
     */
    public CommitResponse createCommit(CreateCommitRequest req, Long authorId) {
        Branch branch = findBranchOrThrow(req.getBranchId());
        log.info("User {} committing to branch '{}' in project {}",
                authorId, branch.getName(), branch.getProjectId());

        // Validate tag uniqueness if provided
        if (req.getTag() != null && !req.getTag().isBlank()) {
            commitRepository.findTaggedCommits(branch.getProjectId(), Pageable.unpaged())
                    .forEach(c -> {
                        if (req.getTag().equals(c.getTag())) {
                            throw new DuplicateResourceException(
                                    "Tag '" + req.getTag() + "' already exists in project " + branch.getProjectId());
                        }
                    });
        }

        String hash = generateCommitHash(branch.getProjectId(), req.getMessage(), authorId);

        Commit commit = Commit.builder()
                .commitHash(hash)
                .message(req.getMessage())
                .branch(branch)
                .projectId(branch.getProjectId())
                .authorId(authorId)
                .tag(req.getTag())
                .build();

        // Build file snapshots
        List<CommitFile> commitFiles = req.getFiles().stream()
                .map(entry -> CommitFile.builder()
                        .commit(commit)
                        .fileId(entry.getFileId())
                        .filePath(entry.getFilePath())
                        .content(entry.getContent())
                        .changeType(entry.getChangeType())
                        .build())
                .collect(Collectors.toList());

        commit.setFiles(commitFiles);
        Commit saved = commitRepository.save(commit);

        // Advance branch HEAD
        branch.setHeadCommitId(saved.getId());
        branchRepository.save(branch);

        log.info("Commit {} created on branch '{}' with {} files",
                hash.substring(0, 8), branch.getName(), commitFiles.size());
        return toCommitResponse(saved, true);
    }

    /** Paginated commit history for a branch, newest first. */
    @Transactional(readOnly = true)
    public Page<CommitResponse> getBranchHistory(Long branchId, Pageable pageable) {
        findBranchOrThrow(branchId);
        return commitRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable)
                .map(c -> toCommitResponse(c, false));
    }

    /** Paginated commit history across all branches of a project. */
    @Transactional(readOnly = true)
    public Page<CommitResponse> getProjectHistory(Long projectId, Pageable pageable) {
        return commitRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
                .map(c -> toCommitResponse(c, false));
    }

    /** Get a single commit with all file snapshots (detailed view). */
    @Transactional(readOnly = true)
    public CommitResponse getCommit(Long commitId) {
        Commit commit = findCommitOrThrow(commitId);
        return toCommitResponse(commit, true);
    }

    /** Get a commit by its hash. */
    @Transactional(readOnly = true)
    public CommitResponse getCommitByHash(String hash) {
        Commit commit = commitRepository.findByCommitHash(hash)
                .orElseThrow(() -> new ResourceNotFoundException("Commit", "hash", hash));
        return toCommitResponse(commit, true);
    }

    /** Get all tagged commits (releases) for a project. */
    @Transactional(readOnly = true)
    public Page<CommitResponse> getTaggedCommits(Long projectId, Pageable pageable) {
        return commitRepository.findTaggedCommits(projectId, pageable)
                .map(c -> toCommitResponse(c, false));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── DIFF ────────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Compute a diff between two commits.
     *
     * For each file path:
     *  - present in both commits → compare content → MODIFIED or UNCHANGED
     *  - only in fromCommit   → DELETED
     *  - only in toCommit     → ADDED
     */
    @Transactional(readOnly = true)
    public DiffResponse diffCommits(Long fromCommitId, Long toCommitId) {
        Commit from = findCommitOrThrow(fromCommitId);
        Commit to   = findCommitOrThrow(toCommitId);

        Map<String, String> fromFiles = commitFileRepository.findByCommitId(fromCommitId)
                .stream().collect(Collectors.toMap(CommitFile::getFilePath,
                        cf -> cf.getContent() != null ? cf.getContent() : ""));

        Map<String, String> toFiles = commitFileRepository.findByCommitId(toCommitId)
                .stream().collect(Collectors.toMap(CommitFile::getFilePath,
                        cf -> cf.getContent() != null ? cf.getContent() : ""));

        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(fromFiles.keySet());
        allPaths.addAll(toFiles.keySet());

        List<DiffResponse.FileDiff> diffs = new ArrayList<>();
        for (String path : allPaths) {
            boolean inFrom = fromFiles.containsKey(path);
            boolean inTo   = toFiles.containsKey(path);
            String changeType;
            if (!inFrom)       changeType = "ADDED";
            else if (!inTo)    changeType = "DELETED";
            else if (!fromFiles.get(path).equals(toFiles.get(path))) changeType = "MODIFIED";
            else               continue; // unchanged – skip

            diffs.add(DiffResponse.FileDiff.builder()
                    .filePath(path)
                    .changeType(changeType)
                    .contentBefore(inFrom ? fromFiles.get(path) : null)
                    .contentAfter(inTo   ? toFiles.get(path)   : null)
                    .build());
        }

        diffs.sort(Comparator.comparing(DiffResponse.FileDiff::getFilePath));

        return DiffResponse.builder()
                .fromCommitHash(from.getCommitHash())
                .toCommitHash(to.getCommitHash())
                .totalChanged(diffs.size())
                .diffs(diffs)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── Private Helpers ─────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    private Branch findBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", id));
    }

    private Commit findCommitOrThrow(Long id) {
        return commitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commit", "id", id));
    }

    /**
     * Generate a unique 8-char commit hash from project ID, message, author, and current time.
     * Uses SHA-256; truncated to 16 hex chars for readability.
     */
    private String generateCommitHash(Long projectId, String message, Long authorId) {
        try {
            String raw = projectId + "|" + message + "|" + authorId + "|" + LocalDateTime.now();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString(); // full 64-char hash stored
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    // ── Mappers ────────────────────────────────────────────────────────────

    private BranchResponse toBranchResponse(Branch b) {
        return BranchResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .projectId(b.getProjectId())
                .createdBy(b.getCreatedBy())
                .headCommitId(b.getHeadCommitId())
                .defaultBranch(b.isDefaultBranch())
                .commitCount(commitRepository.countByBranchId(b.getId()))
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    private CommitResponse toCommitResponse(Commit c, boolean withFiles) {
        List<CommitResponse.CommitFileResponse> fileResps = null;
        if (withFiles) {
            fileResps = c.getFiles().stream()
                    .map(cf -> CommitResponse.CommitFileResponse.builder()
                            .id(cf.getId())
                            .fileId(cf.getFileId())
                            .filePath(cf.getFilePath())
                            .content(cf.getContent())
                            .changeType(cf.getChangeType())
                            .build())
                    .collect(Collectors.toList());
        }
        return CommitResponse.builder()
                .id(c.getId())
                .commitHash(c.getCommitHash())
                .message(c.getMessage())
                .branchId(c.getBranch().getId())
                .branchName(c.getBranch().getName())
                .projectId(c.getProjectId())
                .authorId(c.getAuthorId())
                .tag(c.getTag())
                .fileCount(c.getFiles().size())
                .createdAt(c.getCreatedAt())
                .files(fileResps)
                .build();
    }
}
