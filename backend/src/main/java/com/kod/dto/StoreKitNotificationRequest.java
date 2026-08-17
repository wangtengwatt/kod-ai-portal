package com.kod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreKitNotificationRequest(
        @NotBlank @Size(max = 131072) String signedPayload) {
}
