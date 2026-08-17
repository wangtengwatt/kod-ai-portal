package com.kod.service;

import com.kod.config.IdentityProperties;
import com.kod.dto.KaiIdentityLoginResponse;
import com.kod.entity.User;
import com.kod.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KaiIdentityServiceTest {

    @Mock
    private KaiIdentityUserInfoClient userInfoClient;
    @Mock
    private UserMapper userMapper;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private AuthService authService;

    private IdentityProperties properties;
    private KaiIdentityService service;

    @BeforeEach
    void setUp() {
        properties = new IdentityProperties();
        properties.setEnabled(true);
        properties.setClientId("client-id");
        service = new KaiIdentityService(properties, userInfoClient, userMapper, jdbc, authService);
    }

    @Test
    void returnsASeparatePublicClientConfigurationForIos() {
        properties.setIosClientId("kod-ios");
        properties.setIosRedirectUri("https://kod.kai.com/auth/ios/callback");

        var config = service.getPublicConfig("ios");

        assertTrue(config.enabled());
        assertEquals("kod-ios", config.clientId());
        assertEquals("https://kod.kai.com/auth/ios/callback", config.redirectUri());
    }

    @Test
    void keepsIosLoginDisabledUntilItsClientIsRegistered() {
        var config = service.getPublicConfig("ios");

        assertFalse(config.enabled());
        assertEquals("", config.clientId());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void provisionsAndLogsInVerifiedIdentityWhenKodAccountDoesNotExist() {
        var info = new KaiIdentityUserInfoClient.UserInfo("kai-subject", "new-user@kai.com");
        when(userInfoClient.fetch("access-token")).thenReturn(info);
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        when(userMapper.selectOne(any())).thenReturn(null);

        User created = new User();
        created.setId(42L);
        created.setEmail(info.email());
        when(authService.provisionIdentityUser(info.email())).thenReturn(created);
        when(authService.issueTokenForUser(42L)).thenReturn("kod-token");
        when(authService.issueRefreshTokenForUser(42L)).thenReturn("kod-refresh-token");

        KaiIdentityLoginResponse response = service.exchange("access-token");

        assertEquals("kod-token", response.token());
        assertEquals("kod-refresh-token", response.refreshToken());
        assertEquals(info.email(), response.email());
        assertTrue(response.newUser());
        verify(authService).provisionIdentityUser(info.email());
        verify(authService).issueTokenForUser(42L);
        verify(authService).issueRefreshTokenForUser(42L);
    }
}
