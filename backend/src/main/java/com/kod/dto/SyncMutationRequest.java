package com.kod.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SyncMutationRequest(
        @NotBlank @Size(max = 64) String mutationId,
        @NotBlank @Size(max = 64) String deviceId,
        @NotBlank @Size(max = 32) String entityType,
        @NotBlank @Size(max = 191) String entityId,
        Long baseRevision,
        @NotNull Long clientUpdatedAt,
        boolean deleted,
        boolean sensitive,
        JsonNode payload) {
}
