package com.juliashtal.devanalytics.metrics.calc;

import com.juliashtal.devanalytics.metrics.model.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@code metric_snapshots} table comment against the calculators it describes.
 *
 * <p>The comment is the schema-level answer to "which shape is this metric stored in". Reading it
 * back out of {@code pg_description} rather than restating it here is what makes an omission a
 * build failure.</p>
 */
@SpringBootTest
class MetricSnapshotTableCommentTest {

    @Autowired JdbcTemplate jdbc;

    private String comment;

    @BeforeEach
    void readComment() {
        comment = jdbc.queryForObject(
                "SELECT obj_description('metric_snapshots'::regclass)", String.class);
        assertThat(comment).as("metric_snapshots must carry a table comment").isNotBlank();
    }

    @Test
    void tableComment_aggregateSection_namesExactlyTheTypesCalculatorsStoreWithAPeriod() {
        assertThat(typesIn(sectionAfter("AGGREGATE")))
                .isEqualTo(AggregateStorageShapeDriftTest.EXPECTED_PERIOD_STORED);
    }

    @Test
    void tableComment_dailySection_namesExactlyTheTypesNotStoredWithAPeriod() {
        Set<MetricType> expectedDaily = EnumSet.complementOf(
                EnumSet.copyOf(AggregateStorageShapeDriftTest.EXPECTED_PERIOD_STORED));
        assertThat(typesIn(sectionBetween("DAILY", "AGGREGATE"))).isEqualTo(expectedDaily);
    }

    /** An omission is only detectable by exhaustiveness: a missing type is silently absent. */
    @Test
    void tableComment_bothSections_accountForEveryMetricType() {
        Set<MetricType> documented = EnumSet.noneOf(MetricType.class);
        documented.addAll(typesIn(sectionBetween("DAILY", "AGGREGATE")));
        documented.addAll(typesIn(sectionAfter("AGGREGATE")));
        assertThat(documented).containsExactlyInAnyOrder(MetricType.values());
    }

    /** Half of what V66 corrected was a class name; the type lists above do not cover it. */
    @Test
    void tableComment_writerReference_namesTheClassThatActuallyWrites() {
        assertThat(comment).contains(MetricSnapshotWriter.class.getSimpleName());
    }

    private Set<MetricType> typesIn(String text) {
        return Arrays.stream(MetricType.values())
                .filter(t -> Pattern.compile("\\b" + t.name() + "\\b").matcher(text).find())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MetricType.class)));
    }

    private String sectionAfter(String marker) {
        int i = comment.indexOf(marker);
        assertThat(i).as("comment must contain a %s section", marker).isNotNegative();
        return comment.substring(i);
    }

    private String sectionBetween(String start, String end) {
        int i = comment.indexOf(start);
        int j = comment.indexOf(end);
        assertThat(i).as("comment must contain a %s section", start).isNotNegative();
        assertThat(j).as("comment must contain a %s section", end).isGreaterThan(i);
        return comment.substring(i, j);
    }
}
