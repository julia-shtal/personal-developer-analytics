package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.datasource.repository.DataSourceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time migration runner that re-encrypts legacy base64 tokens with AES-256-GCM.
 *
 * <p>Enabled only when {@code app.encryption.migrate-on-startup=true}. Safe to run more
 * than once — AES-GCM values don't match the legacy prefix, so they are skipped.
 * After a successful migration, revert the flag to {@code false}.
 */
@Component
@ConditionalOnProperty(name = "app.encryption.migrate-on-startup", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TokenReEncryptionRunner implements ApplicationRunner {

    private final DataSourceConfigRepository configRepository;
    private final AesGcmTokenEncryptor encryptor;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Token re-encryption migration started");
        int migrated = 0, skipped = 0, failed = 0;

        for (var config : configRepository.findAll()) {
            String stored = config.getApiTokenEncrypted();
            if (stored == null) {
                skipped++;
                continue;
            }
            try {
                if (encryptor.isLegacyFormat(stored)) {
                    String plain = encryptor.decrypt(stored);   // uses legacy path
                    config.setApiTokenEncrypted(encryptor.encrypt(plain));
                    configRepository.save(config);
                    migrated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("Re-encryption failed for DataSourceConfig id={}: {}", config.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Token re-encryption complete: migrated={}, skipped={}, failed={}", migrated, skipped, failed);
    }
}
