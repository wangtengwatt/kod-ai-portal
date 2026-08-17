package com.kod.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkerCompleteRequest(
        boolean success,
        JsonNode result,
        @Size(max = 4000) String error) {
}
