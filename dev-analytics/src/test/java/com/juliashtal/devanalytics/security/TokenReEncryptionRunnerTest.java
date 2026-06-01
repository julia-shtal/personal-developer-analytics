package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenReEncryptionRunnerTest {

    private static final String KEY_B64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String JWT_SECRET = "test-jwt-secret";

    @Mock DataSourceConfigRepository configRepository;

    AesGcmTokenEncryptor encryptor;
    TokenReEncryptionRunner runner;

    @BeforeEach
    void setUp() {
        encryptor = new AesGcmTokenEncryptor(KEY_B64, JWT_SECRET);
        runner = new TokenReEncryptionRunner(configRepository, encryptor);
    }

    @Test
    void run_migratesLegacyToken_andSaves() throws Exception {
        String plain = "legacy-token-value";
        String legacy = Base64.getEncoder().encodeToString(
                (JWT_SECRET + ":" + plain).getBytes(StandardCharsets.UTF_8));

        DataSourceConfig config = new DataSourceConfig();
        config.setApiTokenEncrypted(legacy);
        when(configRepository.findAll()).thenReturn(List.of(config));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<DataSourceConfig> captor = ArgumentCaptor.forClass(DataSourceConfig.class);
        verify(configRepository).save(captor.capture());
        String saved = captor.getValue().getApiTokenEncrypted();
        // Saved value is now AES-GCM, not legacy format
        assertThat(encryptor.isLegacyFormat(saved)).isFalse();
        // Decrypts back to the original plain
        assertThat(encryptor.decrypt(saved)).isEqualTo(plain);
    }

    @Test
    void run_skipsAlreadyEncryptedToken() throws Exception {
        String plain = "already-gcm-token";
        String gcmValue = encryptor.encrypt(plain);

        DataSourceConfig config = new DataSourceConfig();
        config.setApiTokenEncrypted(gcmValue);
        when(configRepository.findAll()).thenReturn(List.of(config));

        runner.run(new DefaultApplicationArguments());

        verify(configRepository, never()).save(any());
    }

    @Test
    void run_isIdempotent_secondRunSkipsAll() throws Exception {
        String plain = "idempotent-token";
        String legacy = Base64.getEncoder().encodeToString(
                (JWT_SECRET + ":" + plain).getBytes(StandardCharsets.UTF_8));

        DataSourceConfig config = new DataSourceConfig();
        config.setApiTokenEncrypted(legacy);
        when(configRepository.findAll()).thenReturn(List.of(config));
        when(configRepository.save(any())).thenAnswer(inv -> {
            // Simulate the save updating the stored value
            DataSourceConfig saved = inv.getArgument(0);
            config.setApiTokenEncrypted(saved.getApiTokenEncrypted());
            return saved;
        });

        runner.run(new DefaultApplicationArguments()); // first run — migrates
        runner.run(new DefaultApplicationArguments()); // second run — skips

        verify(configRepository, times(1)).save(any()); // saved exactly once
    }
}
