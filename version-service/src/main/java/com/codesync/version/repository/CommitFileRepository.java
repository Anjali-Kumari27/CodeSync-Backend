package com.codesync.version.repository;

import com.codesync.version.model.CommitFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CommitFile}.
 */
@Repository
public interface CommitFileRepository extends JpaRepository<CommitFile, Long> {

    /** All file snapshots in a specific commit. */
    List<CommitFile> findByCommitId(Long commitId);

    /** Find a specific file snapshot by commit and path. */
    Optional<CommitFile> findByCommitIdAndFilePath(Long commitId, String filePath);

    /** All snapshots for a specific file across all commits on a branch. */
    List<CommitFile> findByFileIdOrderByCommitCreatedAtDesc(Long fileId);
}
