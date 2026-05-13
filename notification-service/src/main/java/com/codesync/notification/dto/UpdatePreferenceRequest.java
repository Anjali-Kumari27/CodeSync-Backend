package com.codesync.notification.dto;

import lombok.Data;

/**
 * Request DTO for updating notification preferences.
 * All fields optional — only set fields will be updated.
 */
@Data
public class UpdatePreferenceRequest {
    private Boolean commentInApp;
    private Boolean commentEmail;
    private Boolean mentionInApp;
    private Boolean mentionEmail;
    private Boolean versionInApp;
    private Boolean versionEmail;
    private Boolean executionInApp;
    private Boolean executionEmail;
    private Boolean collabInApp;
    private Boolean collabEmail;
    private Boolean projectInApp;
    private Boolean projectEmail;
    private Boolean systemInApp;
    private Boolean systemEmail;
}
