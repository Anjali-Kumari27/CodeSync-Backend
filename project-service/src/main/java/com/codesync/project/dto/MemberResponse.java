package com.codesync.project.dto;

import com.codesync.project.model.MemberRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for a project member.
 */
@Data
@Builder
public class MemberResponse {

    private Long id;
    private Long projectId;
    private Long userId;
    private MemberRole role;
    private LocalDateTime joinedAt;
}
