package com.juliashtal.devanalytics.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * TODO
 * Очень упрощённый пример.
 * В проде нужно нормальное AES шифрование.
 */
@Component
public class SimpleTokenEncryptor {

    private final String secret;

    public SimpleTokenEncryptor(@Value("${app.jwt.secret:change-me-token-secret}") String secret) {
        this.secret = secret;
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        // Фейковая "шифровка": secret + base64
        return Base64.getEncoder().encodeToString((secret + ":" + plain).getBytes(StandardCharsets.UTF_8));
    }

    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        String decoded = new String(Base64.getDecoder().decode(encrypted), StandardCharsets.UTF_8);
        // вырезаем prefix secret:
        if (decoded.startsWith(secret + ":")) {
            return decoded.substring(secret.length() + 1);
        }
        return decoded; // fallback
    }
}

