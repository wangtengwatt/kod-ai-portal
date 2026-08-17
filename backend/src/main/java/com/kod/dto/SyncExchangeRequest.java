package com.kod.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SyncExchangeRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @Min(0) long cursor,
        @NotNull @Size(max = 200) List<@Valid SyncMutationRequest> mutations) {
}
