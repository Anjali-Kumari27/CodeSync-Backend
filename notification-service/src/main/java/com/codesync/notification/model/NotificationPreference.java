package com.codesync.notification.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * NotificationPreference – per-user settings controlling which events
 * trigger in-app and/or email notifications.
 *
 * One row per user. Created with defaults on first notification delivery.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Receive in-app notification for comments */
    @Builder.Default private boolean commentInApp  = true;
    /** Receive email for comments */
    @Builder.Default private boolean commentEmail  = false;

    @Builder.Default private boolean mentionInApp  = true;
    @Builder.Default private boolean mentionEmail  = true;

    @Builder.Default private boolean versionInApp  = true;
    @Builder.Default private boolean versionEmail  = false;

    @Builder.Default private boolean executionInApp = true;
    @Builder.Default private boolean executionEmail = false;

    @Builder.Default private boolean collabInApp   = true;
    @Builder.Default private boolean collabEmail   = false;

    @Builder.Default private boolean projectInApp  = true;
    @Builder.Default private boolean projectEmail  = true;

    @Builder.Default private boolean systemInApp   = true;
    @Builder.Default private boolean systemEmail   = true;
}
