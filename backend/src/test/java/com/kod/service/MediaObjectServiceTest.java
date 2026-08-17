package com.kod.service;

import com.kod.common.BizException;
import com.kod.config.MediaStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaObjectServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock S3Client s3;

    private MediaStorageProperties properties;
    private MediaObjectService service;

    @BeforeEach
    void setUp() {
        properties = new MediaStorageProperties();
        properties.setEnabled(true);
        properties.setBucket("private-media");
        properties.setMaxObjectBytes(1024);
        properties.setMaxUserBytes(4096);
        service = new MediaObjectService(jdbc, properties, s3);
    }

    @Test
    void rejectsKeysOutsideTheMediaNamespace() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", pngBytes());
        BizException error = assertThrows(BizException.class,
                () -> service.upload(42L, "file:secret", file));
        assertEquals(400, error.getCode());
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadsDetectedImageAndPersistsOnlyPrivateObjectMetadata() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(42L))).thenReturn(0L);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(42L), eq("picture:test:1")))
                .thenReturn(List.of());
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", pngBytes());

        var result = service.upload(42L, "picture:test:1", file);

        assertEquals("picture:test:1", result.get("key"));
        assertEquals("image/png", result.get("contentType"));
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(jdbc).update(anyString(), eq(42L), eq("picture:test:1"), anyString(), eq("image/png"),
                eq((long) pngBytes().length), any(Long.class), any(Long.class));
    }

    @Test
    void refusesUploadsWhenStorageIsNotConfigured() {
        properties.setEnabled(false);
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", pngBytes());
        BizException error = assertThrows(BizException.class,
                () -> service.upload(42L, "picture:test:1", file));
        assertEquals(503, error.getCode());
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0, 0, 0, 0x0d, 0x49, 0x48, 0x44, 0x52};
    }
}
