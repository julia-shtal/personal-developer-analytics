package com.juliashtal.devanalytics.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM token encryptor. Replaces the old base64-only SimpleTokenEncryptor.
 *
 * <p>Format: Base64( IV[12] || Ciphertext+AuthTag ).
 * The 128-bit GCM authentication tag is appended by the JCE cipher and verified on decrypt,
 * so any single-byte tamper causes an AEADBadTagException (wrapped as IllegalStateException).
 *
 * <p>Key: 32-byte value, base64-encoded, from {@code app.encryption.key}
 * (env var {@code ENCRYPTION_KEY}). Generate with: {@code openssl rand -base64 32}.
 * Must be kept separate from the JWT signing secret.
 *
 * <p>Legacy fallback: values encrypted by the old scheme (base64 of jwtSecret+":"+token)
 * are transparently decrypted and re-writable via {@link #isLegacyFormat}.
 */
@Component
@Slf4j
public class AesGcmTokenEncryptor implements TokenEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final String legacyPrefix;

    public AesGcmTokenEncryptor(
            @Value("${app.encryption.key}") String encryptionKeyB64,
            @Value("${app.jwt.secret:change-me-token-secret}") String jwtSecret) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(encryptionKeyB64), "AES");
        this.legacyPrefix = jwtSecret + ":";
    }

    @Override
    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        if (isLegacyFormat(encrypted)) {
            return legacyDecrypt(encrypted);
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed — possibly tampered ciphertext or wrong key", e);
        }
    }

    /** Returns true if {@code encrypted} was produced by the old base64(secret+":"+token) scheme. */
    boolean isLegacyFormat(String encrypted) {
        if (encrypted == null) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(encrypted), StandardCharsets.UTF_8);
            return decoded.startsWith(legacyPrefix);
        } catch (Exception e) {
            return false;
        }
    }

    private String legacyDecrypt(String encrypted) {
        String decoded = new String(Base64.getDecoder().decode(encrypted), StandardCharsets.UTF_8);
        return decoded.substring(legacyPrefix.length());
    }
}
