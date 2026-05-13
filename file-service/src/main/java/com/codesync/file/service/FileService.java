package com.codesync.file.service;

import com.codesync.file.dto.*;
import com.codesync.file.exception.DuplicatePathException;
import com.codesync.file.exception.FileDeletedExcepion;
import com.codesync.file.exception.InvalidFileOperationException;
import com.codesync.file.exception.ResourceNotFoundException;
import com.codesync.file.model.CodeFile;
import com.codesync.file.model.FileType;
import com.codesync.file.repository.CodeFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business logic for the File Service.
 *
 * Features:
 *  - Create, read, update, delete files and directories
 *  - Hierarchical tree view of a project's file system
 *  - Keyword search across file names and content
 *  - Move / rename files and directories
 *  - Soft-delete individual files or entire directory trees
 *  - MIME type inference from file extension
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FileService {

    private final CodeFileRepository fileRepository;

    private void validateReadOnly(Long userId, Long projectId) {

        // TEMPORARY LOGIC
        // later we will connect with project-service

        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Read only access"
            );
        }
    }
    // ═══════════════════════════════════════════════════════════════════════
    // ── CREATE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create a new file or directory within a project.
     */
    public FileResponse createFile(CreateFileRequest req, Long userId) {
    	
    	validateReadOnly(userId, req.getProjectId());
    	
        log.info("User {} creating {} at '{}' in project {}",
                userId, req.getFileType(), req.getFilePath(), req.getProjectId());

        // Resolve parent directory
        CodeFile parent = null;
        String finalPath = req.getFilePath();

        if (req.getParentId() != null) {
            parent = findFileOrThrow(req.getParentId());

            if (parent.getFileType() != FileType.DIRECTORY) {
                throw new InvalidFileOperationException(
                        "Parent node " + req.getParentId() + " is not a directory");
            }

            finalPath = parent.getFilePath() + "/" + req.getFilePath();
        }

        // Check existing path (including deleted rows)
        Optional<CodeFile> existing =
                fileRepository.findByProjectIdAndFilePath(
                        req.getProjectId(), finalPath);

        if (existing.isPresent()) {
            CodeFile old = existing.get();

            // restore deleted file/folder
            if (old.isDeleted()) {
                old.setDeleted(false);
                old.setParent(parent);
                old.setFileType(req.getFileType());
                old.setFileName(extractFileName(finalPath));
                old.setMimeType(inferMimeType(finalPath));
                old.setLastModifiedBy(userId);

                if (req.getFileType() == FileType.FILE) {
                    String content = req.getContent() != null ? req.getContent() : "";
                    old.setContent(content);
                    old.setSizeBytes((long) content.getBytes().length);
                } else {
                    old.setContent(null);
                    old.setSizeBytes(0L);
                }

                old = fileRepository.save(old);

                log.info("Restored deleted {} at '{}'",
                        req.getFileType(), finalPath);

                return toResponse(old, true);
            }

            // active duplicate
            throw new DuplicatePathException(
                    "A file or directory already exists at '" + finalPath + "'");
        }

        // Directories cannot have content
        if (req.getFileType() == FileType.DIRECTORY
                && req.getContent() != null
                && !req.getContent().isEmpty()) {
            throw new InvalidFileOperationException(
                    "Directories cannot have file content");
        }

        String mimeType = req.getMimeType() != null
                ? req.getMimeType()
                : inferMimeType(finalPath);

        String content = req.getFileType() == FileType.FILE
                ? (req.getContent() != null ? req.getContent() : "")
                : null;

        long sizeBytes = content != null ? content.getBytes().length : 0L;

        CodeFile file = CodeFile.builder()
                .filePath(finalPath)
                .fileName(extractFileName(finalPath))
                .fileType(req.getFileType())
                .content(content)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .projectId(req.getProjectId())
                .parent(parent)
                .createdBy(userId)
                .lastModifiedBy(userId)
                .deleted(false)
                .build();

        file = fileRepository.save(file);

        log.info("Created {} id={} at '{}'",
                req.getFileType(), file.getId(), file.getFilePath());

        return toResponse(file, true);
    }
    
    
    // ═══════════════════════════════════════════════════════════════════════
    // ── READ ────────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get file metadata (without content) by ID.
     */
    @Transactional(readOnly = true)
    public FileResponse getFileById(Long fileId) {
        CodeFile file = findFileOrThrow(fileId);
        assertNotDeleted(file);
        return toResponse(file, false);
    }

    /**
     * Get file metadata WITH content by ID.
     */
    @Transactional(readOnly = true)
    public FileResponse getFileWithContent(Long fileId) {
        CodeFile file = findFileOrThrow(fileId);
        assertNotDeleted(file);
        if (file.getFileType() == FileType.DIRECTORY) {
            throw new InvalidFileOperationException(
                    "Cannot read content of a directory node");
        }
        return toResponse(file, true);
    }

    /**
     * Get file by project + path.
     */
    @Transactional(readOnly = true)
    public FileResponse getFileByPath(Long projectId, String filePath) {
        CodeFile file = fileRepository
                .findByProjectIdAndFilePathAndDeletedFalse(projectId, filePath)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "File", "path", filePath));
        return toResponse(file, true);
    }

    /**
     * Get the full directory tree for a project.
     * Returns a nested structure from the root.
     */
    @Transactional(readOnly = true)
    public List<FileResponse> getProjectTree(Long projectId) {
        // Fetch all root-level nodes
        List<CodeFile> roots = fileRepository
                .findByProjectIdAndParentIsNullAndDeletedFalse(projectId);
        return roots.stream()
                .map(f -> toTreeNode(f, projectId))
                .collect(Collectors.toList());
    }

    /**
     * Get direct children of a directory.
     */
    @Transactional(readOnly = true)
    public List<FileResponse> getDirectoryContents(Long projectId, Long dirId) {
        CodeFile dir = findFileOrThrow(dirId);
        assertNotDeleted(dir);
        if (dir.getFileType() != FileType.DIRECTORY) {
            throw new InvalidFileOperationException(dirId + " is not a directory");
        }
        return fileRepository
                .findByProjectIdAndParentIdAndDeletedFalse(projectId, dirId)
                .stream()
                .map(f -> toResponse(f, false))
                .collect(Collectors.toList());
    }

    /**
     * Search files by keyword across file names and content.
     */
    @Transactional(readOnly = true)
    public List<FileResponse> searchFiles(Long projectId, String keyword) {
        return fileRepository.searchByKeyword(projectId, keyword)
                .stream()
                .map(f -> toResponse(f, false))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── UPDATE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Update file content, rename, or move to a different directory.
     * Supports partial PATCH semantics – only non-null fields are applied.
     */
    public FileResponse updateFile(Long fileId, UpdateFileRequest req, Long userId) {
        CodeFile file = findFileOrThrow(fileId);
        
        validateReadOnly(userId, file.getProjectId());
        
        assertNotDeleted(file);
        log.info("User {} updating file id={}", userId, fileId);

        // Content update (FILES only)
        if (req.getContent() != null) {
            if (file.getFileType() == FileType.DIRECTORY) {
                throw new InvalidFileOperationException(
                        "Cannot set content on a directory node");
            }
            file.setContent(req.getContent());
            file.setSizeBytes((long) req.getContent().getBytes().length);
        }

        // Rename / move – update filePath & fileName
        if (req.getFilePath() != null && !req.getFilePath().equals(file.getFilePath())) {
            // Ensure new path doesn't conflict
            if (fileRepository.existsByProjectIdAndFilePathAndDeletedFalse(
                    file.getProjectId(), req.getFilePath())) {
                throw new DuplicatePathException(
                        "A file already exists at '" + req.getFilePath() + "'");
            }
            file.setFilePath(req.getFilePath());
            file.setFileName(extractFileName(req.getFilePath()));
        }

        // Explicit fileName override
        if (req.getFileName() != null) {
            file.setFileName(req.getFileName());
        }

        // MIME type override
        if (req.getMimeType() != null) {
            file.setMimeType(req.getMimeType());
        }

        // Move to a different parent directory
        if (req.getParentId() != null) {
            CodeFile newParent = findFileOrThrow(req.getParentId());
            if (newParent.getFileType() != FileType.DIRECTORY) {
                throw new InvalidFileOperationException(
                        "Target parent " + req.getParentId() + " is not a directory");
            }
            file.setParent(newParent);
        }

        file.setLastModifiedBy(userId);
        file = fileRepository.save(file);
        log.info("File id={} updated successfully", fileId);
        return toResponse(file, true);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── DELETE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Soft-delete a single file.
     */
    public void deleteFile(Long fileId, Long userId) {
        CodeFile file = findFileOrThrow(fileId);
        
        validateReadOnly(userId, file.getProjectId());
        
        assertNotDeleted(file);
        if (file.getFileType() == FileType.DIRECTORY) {
            throw new InvalidFileOperationException(
                    "Use DELETE /api/files/directory/{id} to delete a directory and its contents");
        }
        file.setDeleted(true);
        file.setLastModifiedBy(userId);
        fileRepository.save(file);
        log.info("File id={} soft-deleted by user {}", fileId, userId);
    }

    /**
     * Soft-delete a directory and ALL descendants recursively.
     *
     * @return number of nodes deleted
     */
    public BulkOperationResponse deleteDirectory(Long dirId, Long userId) {
        CodeFile dir = findFileOrThrow(dirId);
        
        validateReadOnly(userId, dir.getProjectId());
        
        assertNotDeleted(dir);
        if (dir.getFileType() != FileType.DIRECTORY) {
            throw new InvalidFileOperationException(dirId + " is not a directory");
        }

        // Collect all descendants by path prefix
        List<CodeFile> descendants = fileRepository
                .findAllUnderDirectory(dir.getProjectId(), dir.getFilePath());

        // Soft-delete all descendants
        descendants.forEach(f -> {
            f.setDeleted(true);
            f.setLastModifiedBy(userId);
        });
        fileRepository.saveAll(descendants);

        // Soft-delete the directory itself
        dir.setDeleted(true);
        dir.setLastModifiedBy(userId);
        fileRepository.save(dir);

        int total = descendants.size() + 1;
        log.info("Directory id={} and {} descendants deleted by user {}", dirId, descendants.size(), userId);
        return BulkOperationResponse.builder()
                .affectedCount(total)
                .message("Deleted directory and " + descendants.size() + " contained file(s)/folder(s)")
                .build();
    }

    /**
     * Hard delete all files/dirs for a project (called when a project is deleted).
     */
    public void deleteAllForProject(Long projectId) {
        List<CodeFile> all = fileRepository.findByProjectIdAndFileTypeAndDeletedFalse(
                projectId, FileType.FILE);
        all.addAll(fileRepository.findByProjectIdAndFileTypeAndDeletedFalse(
                projectId, FileType.DIRECTORY));
        fileRepository.deleteAll(all);
        log.info("Hard-deleted all files for project {}", projectId);
    }

    public FileResponse restoreFile(Long fileId, Long userId) {
        CodeFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", fileId));
        
        if (!file.isDeleted()) {
            throw new InvalidFileOperationException("File is not deleted");
        }
        
        file.setDeleted(false);
        file.setLastModifiedBy(userId);
        file = fileRepository.save(file);
        
        log.info("File id={} restored by user {}", fileId, userId);
        return toResponse(file, false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── Private Helpers ─────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    private CodeFile findFileOrThrow(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", id));
    }

    private void assertNotDeleted(CodeFile file) {
        if (file.isDeleted()) {
            throw new FileDeletedExcepion(
                    "File '" + file.getFilePath() + "' has been deleted");
        }
    }

    /**
     * Build a recursive tree node. Children are loaded lazily per node
     * (acceptable for typical project sizes; add caching for very large trees).
     */
    private FileResponse toTreeNode(CodeFile file, Long projectId) {
        FileResponse resp = toResponse(file, false);
        if (file.getFileType() == FileType.DIRECTORY) {
            List<CodeFile> children = fileRepository
                    .findByProjectIdAndParentIdAndDeletedFalse(projectId, file.getId());
            resp.setChildren(
                    children.stream()
                            .map(c -> toTreeNode(c, projectId))
                            .collect(Collectors.toList())
            );
        }
        return resp;
    }

    /** Map entity → DTO. Content included only when withContent = true. */
    private FileResponse toResponse(CodeFile f, boolean withContent) {
        return FileResponse.builder()
                .id(f.getId())
                .filePath(f.getFilePath())
                .fileName(f.getFileName())
                .fileType(f.getFileType())
                .mimeType(f.getMimeType())
                .sizeBytes(f.getSizeBytes())
                .projectId(f.getProjectId())
                .parentId(f.getParent() != null ? f.getParent().getId() : null)
                .createdBy(f.getCreatedBy())
                .lastModifiedBy(f.getLastModifiedBy())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .deleted(f.isDeleted())
                .content(withContent ? f.getContent() : null)
                .build();
    }

    /** Extract the leaf name from a path string, e.g. "src/main/App.java" → "App.java". */
    private String extractFileName(String filePath) {
        int idx = filePath.lastIndexOf('/');
        return idx >= 0 ? filePath.substring(idx + 1) : filePath;
    }

    /**
     * Infer MIME type from file extension.
     * Falls back to "text/plain" for unknown extensions.
     */
    private String inferMimeType(String filePath) {
        if (filePath == null) return "text/plain";
        int dot = filePath.lastIndexOf('.');
        if (dot < 0) return "text/plain";
        return switch (filePath.substring(dot + 1).toLowerCase()) {
            case "java"       -> "text/x-java";
            case "py"         -> "text/x-python";
            case "js"         -> "text/javascript";
            case "ts"         -> "text/typescript";
            case "jsx"        -> "text/jsx";
            case "tsx"        -> "text/tsx";
            case "html"       -> "text/html";
            case "css"        -> "text/css";
            case "json"       -> "application/json";
            case "xml"        -> "application/xml";
            case "md"         -> "text/markdown";
            case "yml","yaml" -> "text/yaml";
            case "sh"         -> "text/x-sh";
            case "c"          -> "text/x-c";
            case "cpp","cc"   -> "text/x-c++";
            case "go"         -> "text/x-go";
            case "rs"         -> "text/x-rust";
            case "kt"         -> "text/x-kotlin";
            case "rb"         -> "text/x-ruby";
            case "php"        -> "text/x-php";
            case "swift"      -> "text/x-swift";
            case "sql"        -> "text/x-sql";
            case "txt"        -> "text/plain";
            default           -> "text/plain";
        };
    }
}
