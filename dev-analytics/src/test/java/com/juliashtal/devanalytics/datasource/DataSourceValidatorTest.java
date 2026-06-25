package com.juliashtal.devanalytics.datasource;

import com.juliashtal.devanalytics.datasource.model.DataSourceType;
import com.juliashtal.devanalytics.datasource.model.dto.CreateDataSourceRequest;
import com.juliashtal.devanalytics.datasource.service.DataSourceValidator;
import com.juliashtal.devanalytics.datasource.validation.DataSourceValidationRegistry;
import com.juliashtal.devanalytics.datasource.validation.DataSourceValidationRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSourceValidatorTest {

    @Mock DataSourceValidationRegistry registry;
    @Mock DataSourceValidationRule rule;

    @InjectMocks DataSourceValidator validator;

    @Test
    void validateCreate_nullType_throwsWithoutCallingRegistry() {
        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(null);

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    void validateCreate_gitLocalWithTeamId_throwsWithoutCallingRegistry() {
        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.GIT_LOCAL);
        req.setTeamId(5L);

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GIT_LOCAL");
    }

    @Test
    void validateCreate_delegatesToRegistry_andCallsValidate() {
        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.GITHUB);
        when(registry.forType(DataSourceType.GITHUB)).thenReturn(rule);
        doNothing().when(rule).validate(req);

        validator.validateCreate(req);

        verify(rule).validate(req);
    }

    @Test
    void validateCreate_ruleThrows_propagatesException() {
        CreateDataSourceRequest req = new CreateDataSourceRequest();
        req.setType(DataSourceType.JIRA);
        when(registry.forType(DataSourceType.JIRA)).thenReturn(rule);
        doThrow(new IllegalArgumentException("baseUrl is required for HTTP-based data sources"))
                .when(rule).validate(req);

        assertThatThrownBy(() -> validator.validateCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl is required");
    }
}
