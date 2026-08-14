package com.kod.service;

import com.kod.common.BizException;
import com.kod.config.ComputeCenterProperties;
import com.kod.util.ComputeDeliveryCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** 本机内测的私有加密文件存储；数据库中只保存不可遍历的随机文件标识。 */
@Service
@RequiredArgsConstructor
public class ComputePrivateDocumentStorage {

    private final ComputeCenterProperties properties;
    private final ComputeDeliveryCrypto crypto;

    public String store(byte[] content) {
        try {
            Path root = root();
            String fileId = UUID.randomUUID().toString().replace("-", "") + ".bin";
            Files.write(root.resolve(fileId), crypto.encryptBytes(content), StandardOpenOption.CREATE_NEW);
            return fileId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "私有证件文件保存失败");
        }
    }

    public byte[] read(String fileId) {
        try {
            return crypto.decryptBytes(Files.readAllBytes(resolve(fileId)));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(404, "证件文件不存在或无法解密");
        }
    }

    public void delete(String fileId) {
        if (fileId == null || fileId.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(fileId));
        } catch (Exception e) {
            throw new BizException(500, "证件文件清理失败");
        }
    }

    private Path root() throws Exception {
        Path root = properties.getPrivateStorageDirectory();
        Files.createDirectories(root);
        return root;
    }

    private Path resolve(String fileId) throws Exception {
        if (fileId == null || !fileId.matches("[a-f0-9]{32}\\.bin")) {
            throw new BizException(400, "非法证件文件标识");
        }
        Path root = root();
        Path resolved = root.resolve(fileId).normalize();
        if (!resolved.getParent().equals(root)) throw new BizException(400, "非法证件文件路径");
        return resolved;
    }
}
