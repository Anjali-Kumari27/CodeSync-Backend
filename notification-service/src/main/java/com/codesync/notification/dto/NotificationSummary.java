package com.codesync.notification.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for notification inbox summary.
 */
@Data
@Builder
public class NotificationSummary {
    private long totalUnread;
    private long totalCount;
}
