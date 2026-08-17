package com.kod.service;

import com.kod.common.BizException;
import com.kod.config.SyncProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Owner-scoped envelope encryption for synchronized provider credentials. */
@Service
@RequiredArgsConstructor
public class SyncSecretVaultService {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final JdbcTemplate jdbc;
    private final SyncProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String store(Long userId, String plaintext) {
        SecretKey kek = loadMasterKey();
        String id = UUID.randomUUID().toString();
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256, secureRandom);
            SecretKey dek = generator.generateKey();
            byte[] payloadIv = randomIv();
            byte[] dekIv = randomIv();
            byte[] ciphertext = encrypt(dek, payloadIv, aad(userId, id, "payload"),
                    plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] encryptedDek = encrypt(kek, dekIv, aad(userId, id, "dek"), dek.getEncoded());
            jdbc.update("""
                    INSERT INTO kod_secret_vault
                      (id, user_id, encrypted_dek, dek_iv, ciphertext, payload_iv, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, userId, encryptedDek, dekIv, ciphertext, payloadIv, System.currentTimeMillis());
            return id;
        } catch (GeneralSecurityException e) {
            throw new BizException(500, "同步密钥保险库加密失败");
        }
    }

    public String read(Long userId, String id) {
        SecretKey kek = loadMasterKey();
        List<VaultRow> rows = jdbc.query("""
                        SELECT encrypted_dek, dek_iv, ciphertext, payload_iv
                        FROM kod_secret_vault WHERE id=? AND user_id=? LIMIT 1
                        """,
                (rs, rowNum) -> new VaultRow(
                        rs.getBytes("encrypted_dek"),
                        rs.getBytes("dek_iv"),
                        rs.getBytes("ciphertext"),
                        rs.getBytes("payload_iv")), id, userId);
        if (rows.isEmpty()) throw new BizException(500, "同步密钥保险库记录不存在");
        VaultRow row = rows.get(0);
        try {
            byte[] dekBytes = decrypt(kek, row.dekIv(), aad(userId, id, "dek"), row.encryptedDek());
            SecretKey dek = new SecretKeySpec(dekBytes, "AES");
            byte[] plaintext = decrypt(dek, row.payloadIv(), aad(userId, id, "payload"), row.ciphertext());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new BizException(500, "同步密钥保险库解密失败");
        }
    }

    private SecretKey loadMasterKey() {
        if (!StringUtils.hasText(properties.getVaultMasterKey())) {
            throw new BizException(503, "敏感配置同步尚未配置 KMS 主密钥");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(properties.getVaultMasterKey().trim());
            if (decoded.length != 32) throw new IllegalArgumentException("wrong key length");
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException e) {
            throw new BizException(503, "同步 KMS 主密钥格式无效");
        }
    }

    private byte[] randomIv() {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private byte[] encrypt(SecretKey key, byte[] iv, byte[] aad, byte[] plaintext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(plaintext);
    }

    private byte[] decrypt(SecretKey key, byte[] iv, byte[] aad, byte[] ciphertext)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }

    private byte[] aad(Long userId, String id, String purpose) {
        return (userId + ":" + id + ":" + purpose).getBytes(StandardCharsets.UTF_8);
    }

    private record VaultRow(byte[] encryptedDek, byte[] dekIv, byte[] ciphertext, byte[] payloadIv) {
    }
}
