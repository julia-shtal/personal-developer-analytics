package com.juliashtal.devanalytics.security;

/**
 * Encrypts and decrypts stored data-source API tokens.
 */
public interface TokenEncryptor {
    String encrypt(String plain);
    String decrypt(String encrypted);
}
