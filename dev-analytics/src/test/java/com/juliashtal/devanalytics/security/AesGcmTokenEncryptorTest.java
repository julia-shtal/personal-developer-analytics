package com.juliashtal.devanalytics.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class AesGcmTokenEncryptorTest {

    // 32-byte dev key, base64-encoded ("0123456789abcdef0123456789abcdef")
    private static final String KEY_B64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String JWT_SECRET = "test-jwt-secret";

    private AesGcmTokenEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new AesGcmTokenEncryptor(KEY_B64, JWT_SECRET);
    }

    @Test
    void roundTrip_encryptThenDecrypt_returnsOriginal() {
        String plain = "ghp_supersecrettoken123";
        String ciphertext = encryptor.encrypt(plain);

        assertThat(ciphertext).isNotEqualTo(plain);
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plain);
    }

    @Test
    void encrypt_producesUniqueIv_eachCall() {
        String plain = "same-token";
        String c1 = encryptor.encrypt(plain);
        String c2 = encryptor.encrypt(plain);
        assertThat(c1).isNotEqualTo(c2); // different IVs → different ciphertexts
    }

    @Test
    void decrypt_tamperedCiphertext_throwsIllegalState() {
        String ciphertext = encryptor.encrypt("my-secret");
        byte[] raw = Base64.getDecoder().decode(ciphertext);
        raw[raw.length - 1] ^= 0xFF; // flip last byte of auth tag
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_legacyFormat_decryptsTransparently() {
        String plain = "legacy-api-token";
        // Build old-format: base64(jwtSecret + ":" + plain)
        String legacy = Base64.getEncoder().encodeToString(
                (JWT_SECRET + ":" + plain).getBytes(StandardCharsets.UTF_8));

        assertThat(encryptor.isLegacyFormat(legacy)).isTrue();
        assertThat(encryptor.decrypt(legacy)).isEqualTo(plain);
    }

    @Test
    void isLegacyFormat_aesGcmValue_returnsFalse() {
        String ciphertext = encryptor.encrypt("some-token");
        assertThat(encryptor.isLegacyFormat(ciphertext)).isFalse();
    }

    @Test
    void encrypt_null_returnsNull() {
        assertThat(encryptor.encrypt(null)).isNull();
    }

    @Test
    void decrypt_null_returnsNull() {
        assertThat(encryptor.decrypt(null)).isNull();
    }
}
