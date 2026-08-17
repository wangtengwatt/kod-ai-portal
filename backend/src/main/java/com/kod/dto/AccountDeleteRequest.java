package com.kod.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountDeleteRequest(
        String password,
        @NotBlank String confirmation) {
}
