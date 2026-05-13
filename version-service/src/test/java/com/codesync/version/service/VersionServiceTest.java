package com.codesync.version.service;

import com.codesync.version.dto.*;
import com.codesync.version.exception.DuplicateResourceException;
import com.codesync.version.exception.InvalidVersionOperationException;
import com.codesync.version.exception.ResourceNotFoundException;
import com.codesync.version.model.*;
import com.codesync.version.repository.BranchRepository;
import com.codesync.version.repository.CommitFileRepository;
import com.codesync.version.repository.CommitRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VersionService}.
 * Pure Mockito – no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VersionService Unit Tests")
class VersionServiceTest {

    @Mock BranchRepository     branchRepository;
    @Mock CommitRepository     commitRepository;
    @Mock CommitFileRepository commitFileRepository;
    @InjectMocks VersionService versionService;

    private static final Long USER_ID    = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final Long BRANCH_ID  = 100L;
    private static final Long COMMIT_ID  = 200L;

    private Branch sampleBranch() {
        return Branch.builder()
                .id(BRANCH_ID).name("main").projectId(PROJECT_ID)
                .createdBy(USER_ID).defaultBranch(true).build();
    }

    private Commit sampleCommit(Branch branch) {
        return Commit.builder()
                .id(COMMIT_ID).commitHash("abc123").message("Initial commit")
                .branch(branch).projectId(PROJECT_ID).authorId(USER_ID).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("createBranch()")
    class CreateBranch {

        @Test @DisplayName("creates branch and sets default for first branch")
        void createsFirstBranchAsDefault() {
            CreateBranchRequest req = new CreateBranchRequest();
            req.setProjectId(PROJECT_ID);
            req.setName("main");

            when(branchRepository.existsByProjectIdAndName(PROJECT_ID, "main")).thenReturn(false);
            when(branchRepository.countByProjectId(PROJECT_ID)).thenReturn(0L);
            when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> {
                Branch b = inv.getArgument(0);
                b = Branch.builder().id(BRANCH_ID).name(b.getName())
                        .projectId(b.getProjectId()).createdBy(b.getCreatedBy())
                        .defaultBranch(b.isDefaultBranch()).build();
                return b;
            });
            when(commitRepository.countByBranchId(BRANCH_ID)).thenReturn(0L);

            BranchResponse resp = versionService.createBranch(req, USER_ID);

            assertThat(resp.getName()).isEqualTo("main");
            assertThat(resp.isDefaultBranch()).isTrue();
        }

        @Test @DisplayName("non-first branch is not default")
        void nonFirstBranchIsNotDefault() {
            CreateBranchRequest req = new CreateBranchRequest();
            req.setProjectId(PROJECT_ID);
            req.setName("feature/auth");

            when(branchRepository.existsByProjectIdAndName(PROJECT_ID, "feature/auth")).thenReturn(false);
            when(branchRepository.countByProjectId(PROJECT_ID)).thenReturn(1L); // already has branches
            Branch saved = Branch.builder().id(2L).name("feature/auth")
                    .projectId(PROJECT_ID).createdBy(USER_ID).defaultBranch(false).build();
            when(branchRepository.save(any())).thenReturn(saved);
            when(commitRepository.countByBranchId(2L)).thenReturn(0L);

            BranchResponse resp = versionService.createBranch(req, USER_ID);

            assertThat(resp.isDefaultBranch()).isFalse();
        }

        @Test @DisplayName("throws DuplicateResourceException for duplicate name")
        void throwsOnDuplicateName() {
            CreateBranchRequest req = new CreateBranchRequest();
            req.setProjectId(PROJECT_ID);
            req.setName("main");

            when(branchRepository.existsByProjectIdAndName(PROJECT_ID, "main")).thenReturn(true);

            assertThatThrownBy(() -> versionService.createBranch(req, USER_ID))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("main");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("deleteBranch()")
    class DeleteBranch {

        @Test @DisplayName("successfully deletes a non-default branch")
        void deletesNonDefaultBranch() {
            Branch branch = Branch.builder().id(BRANCH_ID).name("feature/x")
                    .projectId(PROJECT_ID).createdBy(USER_ID).defaultBranch(false).build();
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));

            assertThatNoException().isThrownBy(
                    () -> versionService.deleteBranch(BRANCH_ID, USER_ID));
            verify(branchRepository).delete(branch);
        }

        @Test @DisplayName("throws InvalidVersionOperationException for default branch")
        void throwsForDefaultBranch() {
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(sampleBranch()));

            assertThatThrownBy(() -> versionService.deleteBranch(BRANCH_ID, USER_ID))
                    .isInstanceOf(InvalidVersionOperationException.class)
                    .hasMessageContaining("default");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("createCommit()")
    class CreateCommit {

        @Test @DisplayName("creates commit and advances branch HEAD")
        void createsCommitAndAdvancesHead() {
            Branch branch = sampleBranch();
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
            // lenient: only invoked when tag != null; this test has no tag
            lenient().when(commitRepository.findTaggedCommits(eq(PROJECT_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            when(commitRepository.save(any(Commit.class))).thenAnswer(inv -> {
                Commit c = inv.getArgument(0);
                c = Commit.builder().id(COMMIT_ID).commitHash(c.getCommitHash())
                        .message(c.getMessage()).branch(branch).projectId(PROJECT_ID)
                        .authorId(USER_ID).files(c.getFiles()).build();
                return c;
            });
            when(branchRepository.save(any())).thenReturn(branch);

            CreateCommitRequest req = new CreateCommitRequest();
            req.setBranchId(BRANCH_ID);
            req.setMessage("Add login feature");
            CreateCommitRequest.CommitFileEntry entry = new CreateCommitRequest.CommitFileEntry();
            entry.setFilePath("src/Auth.java");
            entry.setContent("class Auth {}");
            entry.setChangeType(ChangeType.ADDED);
            req.setFiles(List.of(entry));

            CommitResponse resp = versionService.createCommit(req, USER_ID);

            assertThat(resp.getMessage()).isEqualTo("Add login feature");
            assertThat(resp.getCommitHash()).isNotBlank();
            assertThat(resp.getFiles()).hasSize(1);
            assertThat(resp.getFiles().get(0).getFilePath()).isEqualTo("src/Auth.java");
            // branch HEAD should be updated
            verify(branchRepository).save(branch);
            assertThat(branch.getHeadCommitId()).isEqualTo(COMMIT_ID);
        }

        @Test @DisplayName("throws DuplicateResourceException for duplicate tag")
        void throwsForDuplicateTag() {
            Branch branch = sampleBranch();
            when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));

            Commit existing = sampleCommit(branch);
            existing.setTag("v1.0.0");
            when(commitRepository.findTaggedCommits(eq(PROJECT_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(existing)));

            CreateCommitRequest req = new CreateCommitRequest();
            req.setBranchId(BRANCH_ID);
            req.setMessage("Release");
            req.setTag("v1.0.0");
            req.setFiles(List.of());

            assertThatThrownBy(() -> versionService.createCommit(req, USER_ID))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("v1.0.0");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("diffCommits()")
    class DiffCommits {

        @Test @DisplayName("correctly identifies ADDED, MODIFIED, and DELETED files")
        void computesDiff() {
            Branch branch = sampleBranch();
            Commit from = sampleCommit(branch);
            Commit to   = sampleCommit(branch);
            to.setId(201L);

            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(from));
            when(commitRepository.findById(201L)).thenReturn(Optional.of(to));

            // from commit: has A.java and B.java
            CommitFile cfA = CommitFile.builder().filePath("A.java")
                    .content("class A {}").changeType(ChangeType.UNCHANGED).commit(from).build();
            CommitFile cfB = CommitFile.builder().filePath("B.java")
                    .content("class B {}").changeType(ChangeType.UNCHANGED).commit(from).build();

            // to commit: A.java modified, C.java added, B.java deleted
            CommitFile cfAMod = CommitFile.builder().filePath("A.java")
                    .content("class A { int x; }").changeType(ChangeType.MODIFIED).commit(to).build();
            CommitFile cfC = CommitFile.builder().filePath("C.java")
                    .content("class C {}").changeType(ChangeType.ADDED).commit(to).build();

            when(commitFileRepository.findByCommitId(COMMIT_ID)).thenReturn(List.of(cfA, cfB));
            when(commitFileRepository.findByCommitId(201L)).thenReturn(List.of(cfAMod, cfC));

            DiffResponse diff = versionService.diffCommits(COMMIT_ID, 201L);

            assertThat(diff.getTotalChanged()).isEqualTo(3);
            assertThat(diff.getDiffs())
                    .extracting(DiffResponse.FileDiff::getFilePath)
                    .containsExactlyInAnyOrder("A.java", "B.java", "C.java");

            // Verify change types
            diff.getDiffs().forEach(d -> {
                switch (d.getFilePath()) {
                    case "A.java" -> assertThat(d.getChangeType()).isEqualTo("MODIFIED");
                    case "B.java" -> assertThat(d.getChangeType()).isEqualTo("DELETED");
                    case "C.java" -> assertThat(d.getChangeType()).isEqualTo("ADDED");
                }
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("getCommitByHash()")
    class GetCommitByHash {

        @Test @DisplayName("returns commit for valid hash")
        void returnsCommit() {
            Branch branch = sampleBranch();
            Commit commit = sampleCommit(branch);
            when(commitRepository.findByCommitHash("abc123")).thenReturn(Optional.of(commit));

            CommitResponse resp = versionService.getCommitByHash("abc123");
            assertThat(resp.getCommitHash()).isEqualTo("abc123");
        }

        @Test @DisplayName("throws ResourceNotFoundException for unknown hash")
        void throwsForUnknownHash() {
            when(commitRepository.findByCommitHash("notreal")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> versionService.getCommitByHash("notreal"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("notreal");
        }
    }
}
