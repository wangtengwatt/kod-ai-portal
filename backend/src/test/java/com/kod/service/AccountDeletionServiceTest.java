package com.kod.service;

import com.kod.common.BizException;
import com.kod.dto.AccountDeleteRequest;
import com.kod.entity.User;
import com.kod.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock StringRedisTemplate redis;
    @Mock MediaObjectService mediaObjectService;

    private AccountDeletionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(jdbc, userMapper, passwordEncoder, redis, mediaObjectService);
        user = new User();
        user.setId(42L);
        user.setPassword("hash");
    }

    @Test
    void requiresExactConfirmationBeforeReadingAccount() {
        BizException error = assertThrows(BizException.class,
                () -> service.delete(42L, new AccountDeleteRequest("password", "delete")));
        assertEquals(400, error.getCode());
        verify(userMapper, never()).selectById(42L);
    }

    @Test
    void passwordAccountMustReauthenticate() {
        when(userMapper.selectById(42L)).thenReturn(user);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(0);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        BizException error = assertThrows(BizException.class,
                () -> service.delete(42L, new AccountDeleteRequest("wrong", "DELETE")));
        assertEquals(401, error.getCode());
    }

    @Test
    void kaiLinkedAccountCanDeleteWithoutUnknownGeneratedPassword() {
        when(userMapper.selectById(42L)).thenReturn(user);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(42L))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(42L))).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("disabled-hash");

        var result = service.delete(42L, new AccountDeleteRequest("", "DELETE"));

        assertTrue((Boolean) result.get("deleted"));
        assertEquals(false, result.get("sandboxPurgePending"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(redis).delete("kod:token:42");
        verify(mediaObjectService).markUserForDeletion(42L);
    }
}
