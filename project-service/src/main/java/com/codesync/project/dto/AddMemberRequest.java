package com.codesync.project.dto;

import com.codesync.project.model.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for adding a member to a project.
 */
@Data
public class AddMemberRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Role is required")
    private MemberRole role;
}
