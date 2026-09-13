package com.juliashtal.devanalytics.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.juliashtal.devanalytics.ai.client.LlmClient;
import com.juliashtal.devanalytics.ai.model.AggregatedMetricsContext;
import com.juliashtal.devanalytics.ai.service.AiContextBuilderService;
import com.juliashtal.devanalytics.ai.service.MetricSummaryPersistenceService;
import com.juliashtal.devanalytics.ai.service.MetricsAiService;
import com.juliashtal.devanalytics.ai.service.PromptVersionProvider;
import com.juliashtal.devanalytics.config.CacheConfig;
import com.juliashtal.devanalytics.git.service.RepoService;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.service.TeamService;
import com.juliashtal.devanalytics.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins that the prompt version is part of the ai_summaries cache key, so a prompt revision is never
 * answered out of the cache with text the previous prompt produced.
 */
@SpringJUnitConfig(MetricsAiServiceCacheTest.CachedServiceConfig.class)
class MetricsAiServiceCacheTest {

    private static final String VALID_JSON = """
            {"headline":"h","overview":"o","insights":[],"recommendations":[]}""";

    private final User user = new User();
    private final LocalDate from = LocalDate.of(2024, 1, 1);
    private final LocalDate to = LocalDate.of(2024, 1, 31);

    /** Read by the stubbed provider, so a test can revise the prompt version mid-flight. */
    private final AtomicReference<String> promptVersion = new AtomicReference<>("1111111111111111");

    @Autowired MetricsAiService service;
    @Autowired LlmClient llmClient;
    @Autowired PromptVersionProvider promptVersionProvider;
    @Autowired AiContextBuilderService contextBuilder;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        reset(llmClient, promptVersionProvider, contextBuilder);
        cacheManager.getCache("ai_summaries").clear();

        when(promptVersionProvider.hashFor(any())).thenAnswer(inv -> promptVersion.get());
        when(contextBuilder.buildPersonalContext(any(), any(), any(), any()))
                .thenReturn(new AggregatedMetricsContext());
        when(llmClient.complete(any(), any(), any(), anyBoolean())).thenReturn(VALID_JSON);
    }

    @Test
    void generateSummary_sameArgumentsAndPromptVersion_servesSecondCallFromCache() {
        service.generateSummary(user, from, to, null);
        service.generateSummary(user, from, to, null);

        verify(llmClient, times(1)).complete(any(), any(), any(), anyBoolean());
    }

    @Test
    void generateSummary_promptVersionChangedBetweenCalls_missesTheCache() {
        service.generateSummary(user, from, to, null);

        promptVersion.set("2222222222222222");
        service.generateSummary(user, from, to, null);

        verify(llmClient, times(2)).complete(any(), any(), any(), anyBoolean());
    }

    @Configuration
    @Import(CacheConfig.class)
    static class CachedServiceConfig {

        @Bean AiContextBuilderService contextBuilder() { return mock(AiContextBuilderService.class); }
        @Bean RepoService repoService() { return mock(RepoService.class); }
        @Bean TeamService teamService() { return mock(TeamService.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean LlmClient llmClient() { return mock(LlmClient.class); }
        @Bean MetricSummaryPersistenceService persistenceService() { return mock(MetricSummaryPersistenceService.class); }
        @Bean PromptVersionProvider promptVersionProvider() { return mock(PromptVersionProvider.class); }

        @Bean
        MetricsAiService metricsAiService(AiContextBuilderService contextBuilder, RepoService repoService,
                                          TeamService teamService, UserService userService, LlmClient llmClient,
                                          MetricSummaryPersistenceService persistenceService,
                                          PromptVersionProvider promptVersionProvider) {
            MetricsAiService service = new MetricsAiService(contextBuilder, repoService, teamService, userService,
                    llmClient, new ObjectMapper().registerModule(new JavaTimeModule()), persistenceService,
                    promptVersionProvider);
            ReflectionTestUtils.setField(service, "model", "llama3.2");
            return service;
        }
    }
}
