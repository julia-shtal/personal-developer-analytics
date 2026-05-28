package com.juliashtal.devanalytics.user.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@Table(name = "user_notification_prefs")
public class UserNotificationPrefsEntity {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private boolean aiBrief = true;

    @Column(nullable = false)
    private boolean syncFailures = true;

    @Column(nullable = false)
    private boolean afterHours = true;

    @Column(nullable = false)
    private boolean newTeamMember = false;

    public UserNotificationPrefsEntity(User user) {
        this.user = user;
    }
}
