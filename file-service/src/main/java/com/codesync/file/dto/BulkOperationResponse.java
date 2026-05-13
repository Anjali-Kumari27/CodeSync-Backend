package com.codesync.file.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for bulk / batch operations (create-directory, delete-tree).
 */
@Data
@Builder
public class BulkOperationResponse {
    private int affectedCount;
    private String message;
}
