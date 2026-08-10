package com.los.core.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PII Encryption Service — AES-256-GCM encryption at rest for sensitive data.
 *
 * Phase A requirement (BR-18): Encrypt PII fields (Aadhaar, PAN, bank account)
 * at rest in the database. Uses AES-256 in GCM mode for authenticated encryption.
 *
 * Key management:
 * - Encryption key is provided via environment variable PII_ENCRYPTION_KEY (base64-encoded)
 * - If not configured, generates a random key and logs a warning (dev/demo mode)
 * - In production, the key should be stored in AWS KMS, HashiCorp Vault, or similar
 *
 * Format: base64( IV[12] || ciphertext || authTag[16] )
 */
@Slf4j
@Service
public class PiiEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int AES_KEY_SIZE = 256;

    @Value("${los.security.pii-encryption-key:}")
    private String configuredKey;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(configuredKey);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException(
                        "PII encryption key must be 256 bits (32 bytes). Got: " + keyBytes.length + " bytes");
            }
            secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("PII encryption initialized with configured key");
        } else {
            // Generate random key for dev/demo — NOT for production
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(AES_KEY_SIZE, secureRandom);
                secretKey = keyGen.generateKey();
                log.warn("[PII-ENCRYPTION] No PII_ENCRYPTION_KEY configured — using random key. "
                        + "Data encrypted in this session will NOT be decryptable after restart. "
                        + "Set PII_ENCRYPTION_KEY env var for production use.");
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize PII encryption", e);
            }
        }
    }

    /**
     * Encrypt a plaintext PII value.
     *
     * @param plaintext The sensitive data to encrypt (e.g., Aadhaar number, PAN)
     * @return Base64-encoded ciphertext (IV + encrypted data + auth tag)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: IV[12] || ciphertext+authTag
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("PII encryption failed: {}", e.getMessage());
            throw new RuntimeException("PII encryption failed", e);
        }
    }

    /**
     * Decrypt a Base64-encoded ciphertext back to plaintext.
     *
     * @param encryptedBase64 The Base64-encoded ciphertext (from encrypt())
     * @return The original plaintext PII value
     */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) {
            return encryptedBase64;
        }

        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);

            // Extract IV from the first 12 bytes
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("PII decryption failed: {}", e.getMessage());
            throw new RuntimeException("PII decryption failed", e);
        }
    }

    /**
     * Check if the encryption service has a persistent key configured.
     * Returns false when using a random dev key (data won't survive restart).
     */
    public boolean isPersistentKeyConfigured() {
        return configuredKey != null && !configuredKey.isBlank();
    }

    /**
     * Generate a new AES-256 key and return it as Base64.
     * Utility method for operators to generate a key for production use.
     */
    public static String generateNewKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, new SecureRandom());
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }
}
