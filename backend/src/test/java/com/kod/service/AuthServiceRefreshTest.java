package com.kod.service;

import com.kod.common.BizException;
import com.kod.config.JwtProperties;
import com.kod.entity.User;
import com.kod.mapper.UserMapper;
import com.kod.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    @Mock private UserMapper userMapper;
    @Mock private RelayStationService relayStationService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private EmailService emailService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setExpireMillis(60_000L);
        properties.setRefreshExpireMillis(120_000L);
        when(redis.opsForValue()).thenReturn(values);
        service = new AuthService(
                userMapper,
                relayStationService,
                passwordEncoder,
                jwtUtil,
                properties,
                redis,
                emailService);
    }

    @Test
    void consumesAndRotatesRefreshToken() {
        User user = new User();
        user.setId(42L);
        when(values.getAndDelete(anyString())).thenReturn("42", (String) null);
        when(userMapper.selectById(42L)).thenReturn(user);
        when(jwtUtil.generateToken(42L)).thenReturn("next-access");

        var response = service.refreshSession("old-refresh");

        assertEquals("next-access", response.token());
        assertTrue(response.refreshToken().length() >= 64);
        assertNotEquals("old-refresh", response.refreshToken());
        verify(values).getAndDelete(anyString());
        verify(values, times(2)).set(anyString(), anyString(), any());
        assertThrows(BizException.class, () -> service.refreshSession("old-refresh"));
    }
}
