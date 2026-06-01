package com.juliashtal.devanalytics.security;

public interface TokenEncryptor {
    String encrypt(String plain);
    String decrypt(String encrypted);
}
