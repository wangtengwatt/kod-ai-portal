package com.kod.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkerEventRequest(
        @NotBlank @Size(max = 32) String type,
        JsonNode payload) {
}
