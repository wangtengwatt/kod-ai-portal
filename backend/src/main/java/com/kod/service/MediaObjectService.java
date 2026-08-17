package com.kod.service;

import com.kod.common.BizException;
import com.kod.config.MediaStorageProperties;
import jakarta.annotation.PreDestroy;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MediaObjectService {
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif", "image/heic", "image/heif", "image/avif",
            "video/mp4", "video/webm", "video/quicktime"
    );

    private final JdbcTemplate jdbc;
    private final MediaStorageProperties properties;
    private final S3Client s3;
    private final boolean ownsClient;
    private final Tika tika = new Tika();

    @Autowired
    public MediaObjectService(JdbcTemplate jdbc, MediaStorageProperties properties) {
        this(jdbc, properties, buildClient(properties), true);
    }

    MediaObjectService(JdbcTemplate jdbc, MediaStorageProperties properties, S3Client s3) {
        this(jdbc, properties, s3, false);
    }

    private MediaObjectService(JdbcTemplate jdbc, MediaStorageProperties properties, S3Client s3, boolean ownsClient) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.s3 = s3;
        this.ownsClient = ownsClient;
    }

    public List<String> missing(Long userId, List<String> requestedKeys) {
        requireEnabled();
        List<String> keys = new ArrayList<>(new LinkedHashSet<>(requestedKeys));
        keys.forEach(this::validateStorageKey);
        if (keys.isEmpty()) return List.of();

        String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(keys);
        Set<String> present = new HashSet<>(jdbc.queryForList(
                "SELECT storage_key FROM kod_media_object WHERE user_id=? AND status='active' AND storage_key IN (" + placeholders + ")",
                String.class, args.toArray()));
        return keys.stream().filter(key -> !present.contains(key)).toList();
    }

    public Map<String, Object> upload(Long userId, String storageKey, MultipartFile file) {
        requireEnabled();
        validateStorageKey(storageKey);
        if (file == null || file.isEmpty()) throw new BizException(400, "Media file is empty");
        if (file.getSize() > properties.getMaxObjectBytes()) throw new BizException(413, "Media file exceeds the size limit");

        String contentType = detectAllowedContentType(file);
        enforceQuota(userId, storageKey, file.getSize());
        String objectKey = objectKey(userId, storageKey);
        try {
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(file.getSize());
            if (StringUtils.hasText(properties.getServerSideEncryption())) {
                request.serverSideEncryption(ServerSideEncryption.fromValue(properties.getServerSideEncryption().trim()));
            }
            try (var input = file.getInputStream()) {
                s3.putObject(request.build(), RequestBody.fromInputStream(input, file.getSize()));
            }
        } catch (Exception error) {
            throw new BizException(503, "Media object storage is temporarily unavailable", error);
        }

        long now = System.currentTimeMillis();
        jdbc.update("""
                INSERT INTO kod_media_object(user_id,storage_key,object_key,content_type,byte_size,status,created_at,updated_at)
                VALUES(?,?,?,?,?,'active',?,?)
                ON DUPLICATE KEY UPDATE object_key=VALUES(object_key),content_type=VALUES(content_type),
                  byte_size=VALUES(byte_size),status='active',updated_at=VALUES(updated_at)
                """, userId, storageKey, objectKey, contentType, file.getSize(), now, now);
        return Map.of("key", storageKey, "byteSize", file.getSize(), "contentType", contentType);
    }

    public Optional<Download> download(Long userId, String storageKey) {
        requireEnabled();
        validateStorageKey(storageKey);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT object_key,content_type,byte_size FROM kod_media_object WHERE user_id=? AND storage_key=? AND status='active'",
                userId, storageKey);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        try {
            ResponseInputStream<GetObjectResponse> stream = s3.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket()).key(String.valueOf(row.get("object_key"))).build());
            return Optional.of(new Download(String.valueOf(row.get("content_type")),
                    ((Number) row.get("byte_size")).longValue(), stream));
        } catch (Exception error) {
            throw new BizException(503, "Media object storage is temporarily unavailable", error);
        }
    }

    public void markUserForDeletion(Long userId) {
        jdbc.update("UPDATE kod_media_object SET status='delete_pending',updated_at=? WHERE user_id=?",
                System.currentTimeMillis(), userId);
    }

    public int purgePending(int limit) {
        if (!properties.isEnabled()) return 0;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id,storage_key,object_key FROM kod_media_object WHERE status='delete_pending' ORDER BY updated_at LIMIT ?",
                Math.max(1, Math.min(limit, 500)));
        int deleted = 0;
        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            String storageKey = String.valueOf(row.get("storage_key"));
            try {
                s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.getBucket()).key(String.valueOf(row.get("object_key"))).build());
                deleted += jdbc.update("DELETE FROM kod_media_object WHERE user_id=? AND storage_key=? AND status='delete_pending'",
                        userId, storageKey);
            } catch (Exception ignored) {
                // Keep the row as delete_pending; the scheduled cleanup will retry.
            }
        }
        return deleted;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new BizException(503, "Cross-device media storage is not configured");
    }

    private void validateStorageKey(String key) {
        if (!StringUtils.hasText(key) || key.length() > 255
                || !(key.startsWith("picture:") || key.startsWith("video:"))) {
            throw new BizException(400, "Invalid media storage key");
        }
    }

    private String detectAllowedContentType(MultipartFile file) {
        try (var input = file.getInputStream()) {
            String detected = tika.detect(input, file.getOriginalFilename());
            if (!ALLOWED_TYPES.contains(detected)) throw new BizException(415, "Unsupported media content type");
            return detected;
        } catch (IOException error) {
            throw new BizException(400, "Unable to inspect media file", error);
        }
    }

    private void enforceQuota(Long userId, String storageKey, long incomingBytes) {
        Long current = jdbc.queryForObject(
                "SELECT COALESCE(SUM(byte_size),0) FROM kod_media_object WHERE user_id=? AND status='active'",
                Long.class, userId);
        List<Long> previous = jdbc.query(
                "SELECT byte_size FROM kod_media_object WHERE user_id=? AND storage_key=? AND status='active'",
                (rs, rowNum) -> rs.getLong(1), userId, storageKey);
        long replacing = previous.isEmpty() ? 0L : previous.get(0);
        long projected = Math.max(0L, current == null ? 0L : current) - replacing + incomingBytes;
        if (projected > properties.getMaxUserBytes()) throw new BizException(413, "Media storage quota exceeded");
    }

    private static String objectKey(Long userId, String storageKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest((userId + ":" + storageKey).getBytes(StandardCharsets.UTF_8));
            return "users/" + userId + "/" + HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static S3Client buildClient(MediaStorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess()).build())
                .overrideConfiguration(config -> config
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .apiCallTimeout(Duration.ofMinutes(3)));
        if (StringUtils.hasText(properties.getEndpoint())) builder.endpointOverride(URI.create(properties.getEndpoint()));
        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @PreDestroy
    public void close() {
        if (ownsClient) s3.close();
    }

    public record Download(String contentType, long byteSize, ResponseInputStream<GetObjectResponse> stream)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }
}
