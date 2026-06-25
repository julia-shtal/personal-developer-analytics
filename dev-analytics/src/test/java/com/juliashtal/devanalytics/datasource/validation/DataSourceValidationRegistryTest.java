package com.juliashtal.devanalytics.datasource.validation;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceValidationRegistryTest {

    private static DataSourceValidationRule stubFor(DataSourceType type) {
        return new DataSourceValidationRule() {
            @Override public DataSourceType supports() { return type; }
            @Override public void validate(CreateDataSourceRequest req) {}
        };
    }

    @Test
    void forType_knownType_returnsRule() {
        DataSourceValidationRegistry registry = new DataSourceValidationRegistry(
                List.of(stubFor(DataSourceType.GIT_LOCAL),
                        stubFor(DataSourceType.GITHUB),
                        stubFor(DataSourceType.JIRA)));

        assertThat(registry.forType(DataSourceType.JIRA).supports()).isEqualTo(DataSourceType.JIRA);
    }

    @Test
    void forType_unknownType_throwsIllegalArgumentException() {
        DataSourceValidationRegistry registry = new DataSourceValidationRegistry(
                List.of(stubFor(DataSourceType.GIT_LOCAL)));

        assertThatThrownBy(() -> registry.forType(DataSourceType.JIRA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JIRA");
    }

    @Test
    void constructor_duplicateSupports_throwsIllegalStateException() {
        assertThatThrownBy(() -> new DataSourceValidationRegistry(
                List.of(stubFor(DataSourceType.GITHUB), stubFor(DataSourceType.GITHUB))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB");
    }
}
