package com.juliashtal.devanalytics.user;

import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.model.UserZone;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the single reading of {@code User.timezone}: every site that attributes activity to a
 * calendar day resolves the zone the same way, including the fallbacks.
 */
class UserZoneTest {

    private static User withTimezone(String timezone) {
        User u = new User();
        u.setId(1L);
        u.setTimezone(timezone);
        return u;
    }

    @Test
    void of_declaredTimezone_returnsThatZone() {
        assertThat(UserZone.of(withTimezone("Pacific/Auckland")))
                .isEqualTo(ZoneId.of("Pacific/Auckland"));
    }

    @Test
    void of_nullTimezone_fallsBackToUtc() {
        assertThat(UserZone.of(withTimezone(null))).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void of_blankTimezone_fallsBackToUtc() {
        assertThat(UserZone.of(withTimezone("   "))).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void of_unparseableTimezone_fallsBackToUtcRatherThanThrowing() {
        assertThat(UserZone.of(withTimezone("Not/AZone"))).isEqualTo(ZoneOffset.UTC);
    }
}
