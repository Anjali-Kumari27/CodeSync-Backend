package com.codesync.project.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectAnalyticsResponse {
    private Long projectId;
    private long totalMembers;
    private long totalStars;
    private long totalForks;
    private long ageInDays;
}
