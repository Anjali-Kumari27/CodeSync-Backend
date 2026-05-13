package com.codesync.file.repository;

import com.codesync.file.model.CodeFile;
import com.codesync.file.model.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for CodeFile.
 */
@Repository
public interface CodeFileRepository extends JpaRepository<CodeFile, Long> {

    /** Root level files/directories */
    List<CodeFile> findByProjectIdAndParentIsNullAndDeletedFalse(Long projectId);

    /** Children of directory */
    List<CodeFile> findByProjectIdAndParentIdAndDeletedFalse(Long projectId, Long parentId);

    /** Files by type */
    List<CodeFile> findByProjectIdAndFileTypeAndDeletedFalse(Long projectId, FileType fileType);

    /** Active file lookup */
    Optional<CodeFile> findByProjectIdAndFilePathAndDeletedFalse(Long projectId, String filePath);

    /** Lookup INCLUDING deleted rows (needed for restore) */
    Optional<CodeFile> findByProjectIdAndFilePath(Long projectId, String filePath);

    /** Active path exists */
    boolean existsByProjectIdAndFilePathAndDeletedFalse(Long projectId, String filePath);

    /** Recursive directory fetch */
    @Query("""
        SELECT f FROM CodeFile f
        WHERE f.projectId = :projectId
          AND f.deleted = false
          AND f.filePath LIKE CONCAT(:dirPath, '/%')
        ORDER BY f.filePath
    """)
    List<CodeFile> findAllUnderDirectory(
            @Param("projectId") Long projectId,
            @Param("dirPath") String dirPath
    );

    /** Search */
    @Query("""
        SELECT f FROM CodeFile f
        WHERE f.projectId = :projectId
          AND f.deleted = false
          AND f.fileType = 'FILE'
          AND (
                LOWER(f.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(f.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
        ORDER BY f.filePath
    """)
    List<CodeFile> searchByKeyword(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword
    );

    /** Count files */
    long countByProjectIdAndFileTypeAndDeletedFalse(Long projectId, FileType fileType);

    /** Deleted files */
    List<CodeFile> findByProjectIdAndDeletedTrue(Long projectId);
}