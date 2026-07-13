package com.juliashtal.devanalytics.user.model;

/**
 * A user's notification toggle preferences and default contact method.
 */
public record NotificationPrefsDto(
        boolean aiBrief,
        boolean syncFailures,
        boolean afterHours,
        boolean newTeamMember,
        ContactMethod defaultContactMethod
) {
    public static NotificationPrefsDto from(UserNotificationPrefsEntity entity) {
        return new NotificationPrefsDto(
                entity.isAiBrief(),
                entity.isSyncFailures(),
                entity.isAfterHours(),
                entity.isNewTeamMember(),
                entity.getDefaultContactMethod()
        );
    }
}
