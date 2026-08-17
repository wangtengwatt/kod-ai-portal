package com.kod.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CloudOperationCreateRequest(
        @NotBlank @Size(max = 16) String kind,
        @NotNull JsonNode params) {
}
