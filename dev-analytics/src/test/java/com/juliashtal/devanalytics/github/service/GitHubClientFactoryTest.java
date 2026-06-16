package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.exception.GitHubException;
import com.juliashtal.devanalytics.security.TokenEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GitHub;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubClientFactoryTest {

    @Mock TokenEncryptor tokenEncryptor;

    @InjectMocks
    GitHubClientFactory factory;

    private DataSourceConfig cfg;

    @BeforeEach
    void setUp() {
        cfg = new DataSourceConfig();
        cfg.setId(1L);
        cfg.setApiTokenEncrypted("enc-tok");
    }

    // ── blank token → IllegalStateException ─────────────────────────────────

    @Test
    void createClient_blankToken_throwsIllegalState() {
        when(tokenEncryptor.decrypt("enc-tok")).thenReturn("   ");

        assertThatThrownBy(() -> factory.createClient(cfg))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub token is missing");
    }

    // ── null token → IllegalStateException ──────────────────────────────────

    @Test
    void createClient_nullToken_throwsIllegalState() {
        when(tokenEncryptor.decrypt("enc-tok")).thenReturn(null);

        assertThatThrownBy(() -> factory.createClient(cfg))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub token is missing");
    }

    // ── null baseUrl → defaults to api.github.com ────────────────────────────

    @Test
    void createClient_nullBaseUrl_returnsClient() {
        when(tokenEncryptor.decrypt("enc-tok")).thenReturn("ghp_valid");
        cfg.setBaseUrl(null);

        GitHub client = factory.createClient(cfg);

        assertThat(client).isNotNull();
    }

    // ── blank baseUrl → defaults to api.github.com ───────────────────────────

    @Test
    void createClient_blankBaseUrl_returnsClient() {
        when(tokenEncryptor.decrypt("enc-tok")).thenReturn("ghp_valid");
        cfg.setBaseUrl("   ");

        GitHub client = factory.createClient(cfg);

        assertThat(client).isNotNull();
    }

    // ── github.com prefix URL → normalised to api.github.com ─────────────────

    @Test
    void createClient_githubComUrl_returnsClient() {
        when(tokenEncryptor.decrypt("enc-tok")).thenReturn("ghp_valid");
        cfg.setBaseUrl("https://github.com/myorg");

        GitHub client = factory.createClient(cfg);

        assertThat(client).isNotNull();
    }

    // ── custom enterprise URL → used as-is ──────────────────────────────────

    @Test
    void createClient_customEnterpriseUrl_returnsClient() {
        when(tokenEncryptor.decrypt("enc-tok")).thenReturn("ghp_valid");
        cfg.setBaseUrl("https://ghe.mycompany.com/api/v3");

        GitHub client = factory.createClient(cfg);

        assertThat(client).isNotNull();
    }

    // ── getDecryptedToken → delegates to TokenEncryptor ─────────────────────

    @Test
    void getDecryptedToken_returnsDecryptedValue() {
        when(tokenEncryptor.decrypt("enc-tok")).thenReturn("ghp_plaintext");

        String result = factory.getDecryptedToken(cfg);

        assertThat(result).isEqualTo("ghp_plaintext");
    }
}
