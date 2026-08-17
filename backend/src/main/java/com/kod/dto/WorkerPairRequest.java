package com.kod.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkerPairRequest(
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull JsonNode capabilities) {
}
