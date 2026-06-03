package com.juliashtal.devanalytics.demo;

import com.juliashtal.devanalytics.metrics.MetricSnapshotRepository;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("demo")
class DataSeederIT {

    @Autowired UserRepository           userRepository;
    @Autowired MetricSnapshotRepository snapshotRepository;
    @Autowired DataSeeder               seeder;

    @Test
    void demoUserIsCreatedOnStartup() {
        assertThat(userRepository.findByEmail("demo@demo.com")).isPresent();
    }

    @Test
    void snapshotsAreSeededForDemoUser() {
        var user = userRepository.findByEmail("demo@demo.com").orElseThrow();
        long count = snapshotRepository.countByUserId(user.getId());
        // 12 weeks x 7 days x 8 daily (user)     = 672
        // 12 weeks x 7 days x 5 daily (teammate) = 420
        // 12 weeks x 11 aggregate personal        = 132
        // 12 weeks x 1 team-aggregate             =  12
        // 12 weeks x 7 days x 1 team-daily        =  84
        // Total (user) = 672 + 132 + 12 + 84 = 900; asserting > 800
        assertThat(count).isGreaterThan(800);
    }

    @Test
    void seederIsIdempotent() {
        var user   = userRepository.findByEmail("demo@demo.com").orElseThrow();
        long before = snapshotRepository.countByUserId(user.getId());
        seeder.run(null);
        long after  = snapshotRepository.countByUserId(user.getId());
        assertThat(after).isEqualTo(before);
    }
}
