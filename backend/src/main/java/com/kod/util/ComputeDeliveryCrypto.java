package com.kod.util;

import com.kod.common.BizException;
import com.kod.config.ComputeCenterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 使用 AES-GCM 加密 GPU 订单交付信息。 */
@Component
@RequiredArgsConstructor
public class ComputeDeliveryCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final ComputeCenterProperties properties;

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new BizException(400, "交付信息不能为空");
        }
        try {
            return Base64.getEncoder().encodeToString(encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "交付信息加密失败");
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return "";
        try {
            return new String(decryptBytes(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "交付信息解密失败");
        }
    }

    public byte[] encryptBytes(byte[] plaintext) throws Exception {
        if (plaintext == null || plaintext.length == 0) throw new BizException(400, "加密内容不能为空");
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plaintext);
        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return payload;
    }

    public byte[] decryptBytes(byte[] payload) throws Exception {
        if (payload == null || payload.length <= IV_LENGTH) throw new BizException(400, "加密内容无效");
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[payload.length - IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
        System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(encrypted);
    }

    public String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "敏感信息指纹生成失败");
        }
    }

    private byte[] keyBytes() throws Exception {
        String secret = properties.getDeliverySecret();
        if (secret.length() < 32) {
            throw new BizException(503, "KOD_COMPUTE_DELIVERY_SECRET 必须配置为至少 32 位随机值");
        }
        return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
    }
}
