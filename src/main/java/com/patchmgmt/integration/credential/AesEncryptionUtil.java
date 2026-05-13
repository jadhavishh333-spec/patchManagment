package com.patchmgmt.integration.credential;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM utility for encrypting SSH passwords stored in the credential_mappings table.
 * Used only in local/dev mode (when CyberArk is not configured).
 * Format: Base64( IV[12 bytes] || Ciphertext+Tag )
 */
public class AesEncryptionUtil {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS = 128;
    private static final int    IV_BYTES     = 12;

    /**
     * Encrypts plaintext using AES-256-GCM.
     * The key is derived from the provided string via SHA-256 (any length accepted).
     * @return Base64-encoded string: IV + ciphertext+tag
     */
    public static String encrypt(String plaintext, String keyString) {
        try {
            byte[] key = deriveKey(keyString);
            byte[] iv  = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(key, "AES"),
                        new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,        IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Password encryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded AES-256-GCM ciphertext back to plaintext.
     */
    public static String decrypt(String encryptedBase64, String keyString) {
        try {
            byte[] key      = deriveKey(keyString);
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            byte[] iv         = new byte[IV_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0,        iv,         0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                        new SecretKeySpec(key, "AES"),
                        new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Password decryption failed", e);
        }
    }

    /** Derives a 256-bit AES key from an arbitrary-length string via SHA-256. */
    private static byte[] deriveKey(String keyString) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                            .digest(keyString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
