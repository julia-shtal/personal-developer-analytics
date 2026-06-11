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
    private boolean aiBrief = false;

    @Column(nullable = false)
    private boolean syncFailures = false;

    @Column(nullable = false)
    private boolean afterHours = false;

    @Column(nullable = false)
    private boolean newTeamMember = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_contact_method", nullable = false, length = 16)
    private ContactMethod defaultContactMethod = ContactMethod.IN_APP;

    public UserNotificationPrefsEntity(User user) {
        this.user = user;
    }
}
