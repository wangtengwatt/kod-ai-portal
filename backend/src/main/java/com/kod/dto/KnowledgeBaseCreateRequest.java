package com.kod.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String embeddingModel,
        @NotBlank @Size(max = 255) String rerankModel,
        @Size(max = 255) String visionModel,
        @Size(max = 32) String providerMode,
        JsonNode documentParser) {
}
