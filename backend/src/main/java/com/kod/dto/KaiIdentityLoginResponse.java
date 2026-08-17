package com.kod.dto;

public record KaiIdentityLoginResponse(String token, String refreshToken, boolean newUser, String email) {
}
