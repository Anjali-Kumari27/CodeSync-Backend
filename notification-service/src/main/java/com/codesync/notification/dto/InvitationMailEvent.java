package com.codesync.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitationMailEvent {

    private String toEmail;
    private String projectName;
    private String role;
    private String invitedBy;
    private String inviteLink;
    
    private Long recipientId;
    private Long projectId;
}