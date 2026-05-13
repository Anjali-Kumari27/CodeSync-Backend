package com.codesync.file.dto;

import com.codesync.file.model.FileType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a file or directory node.
 * Content is included only when explicitly requested (to avoid bloating tree responses).
 */
@Data
@Builder
public class FileResponse {

    private Long id;
    private String filePath;
    private String fileName;
    private FileType fileType;
    private String mimeType;
    private Long sizeBytes;
    private Long projectId;
    private Long parentId;
    private Long createdBy;
    private Long lastModifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    /**
     * Populated only for DIRECTORY nodes in tree responses.
     * Null in flat file responses to save bandwidth.
     */
    private List<FileResponse> children;

    /**
     * File content – included only when caller explicitly requests it
     * (e.g. GET /api/files/{id}/content). Omitted from tree responses.
     */
    private String content;
}
