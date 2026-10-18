package com.jobtracker.email.mailbox;

import com.jobtracker.config.properties.AppProperties;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TokenEncryptionService {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptionService(AppProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new EmailIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not securely store the Gmail refresh token");
        }
    }

    public String decrypt(String encryptedValue) {
        try {
            byte[] packed = Base64.getDecoder().decode(encryptedValue);
            if (packed.length <= IV_LENGTH) {
                throw new GeneralSecurityException("Invalid encrypted token");
            }
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new EmailIntegrationException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not decrypt the stored Gmail refresh token");
        }
    }

    private SecretKeySpec encryptionKey() throws GeneralSecurityException {
        String encodedKey = properties.getSecurity().getTokenEncryptionKey();
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) {
                throw new IllegalArgumentException();
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new GeneralSecurityException("Token encryption key must be base64-encoded 32-byte key");
        }
    }
}
