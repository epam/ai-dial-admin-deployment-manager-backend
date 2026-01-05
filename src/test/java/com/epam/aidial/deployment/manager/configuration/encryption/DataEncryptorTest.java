package com.epam.aidial.deployment.manager.configuration.encryption;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DataEncryptorTest {

    private static final int AES_KEY_SIZE = 192; // AES key size in bits
    private static final int GCM_IV_LENGTH = 12; // IV length for GCM mode
    private static final int GCM_TAG_LENGTH = 16; // Authentication tag length

    private static final String AES = "AES";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";

    @Test
    void testDecryptedKey() throws Exception {
        // Given
        SecretKey masterKey = generateSecretKey(); // Generate a valid AES master key
        SecretKey encryptionKey = generateSecretKey(); // Generate a valid encryption key
        String base64MasterKey = Base64.getEncoder().encodeToString(masterKey.getEncoded());

        // Encrypt the encryption key using the master key
        byte[] iv = generateIv();
        byte[] encryptedData = encryptData(masterKey, iv, encryptionKey.getEncoded());
        String base64EncryptedKey = Base64.getEncoder().encodeToString(encryptedData);

        // When
        byte[] decryptedKey = DataEncryptor.decryptedKey(base64MasterKey, base64EncryptedKey);

        // Then
        assertArrayEquals(encryptionKey.getEncoded(), decryptedKey, "Decrypted key does not match the original encryption key");
    }

    @Test
    void testDecryptedKeyBase64() throws Exception {
        // Given
        SecretKey masterKey = generateSecretKey(); // Generate a valid AES master key
        SecretKey encryptionKey = generateSecretKey(); // Generate a valid encryption key
        String base64MasterKey = Base64.getEncoder().encodeToString(masterKey.getEncoded());
        String base64EncryptionKey = Base64.getEncoder().encodeToString(encryptionKey.getEncoded());

        // Encrypt the encryption key using the master key
        byte[] iv = generateIv();
        byte[] encryptedData = encryptData(masterKey, iv, encryptionKey.getEncoded());
        String base64EncryptedKey = Base64.getEncoder().encodeToString(encryptedData);

        // When
        String decryptedKeyBase64 = DataEncryptor.decryptedKeyBase64(base64MasterKey, base64EncryptedKey);

        // Then
        assertThat(decryptedKeyBase64).isEqualTo(base64EncryptionKey);
    }

    private SecretKey generateSecretKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(AES);
        keyGenerator.init(AES_KEY_SIZE); // Use a 192-bit AES key
        return keyGenerator.generateKey();
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        return iv;
    }

    /**
     * Encrypts data using AES/GCM/NoPadding.
     *
     * @param key   The AES key to use for encryption.
     * @param iv    The initialization vector.
     * @param data  The data to encrypt.
     * @return The encrypted data (IV + ciphertext).
     */
    private byte[] encryptData(SecretKey key, byte[] iv, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * Byte.SIZE, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] ciphertext = cipher.doFinal(data);

        // Combine IV and ciphertext into a single byte array
        byte[] encryptedData = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encryptedData, 0, iv.length);
        System.arraycopy(ciphertext, 0, encryptedData, iv.length, ciphertext.length);

        return encryptedData;
    }
}