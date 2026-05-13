package com.codesync.project.dto;

import com.codesync.project.model.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteMemberRequest {

    @NotBlank
    @Email
    private String email;

    @NotNull
    private MemberRole role;
    
    private Long userId;
}