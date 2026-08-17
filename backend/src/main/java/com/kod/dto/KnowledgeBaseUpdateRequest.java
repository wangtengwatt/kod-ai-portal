package com.kod.dto;

import jakarta.validation.constraints.Size;

public record KnowledgeBaseUpdateRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String rerankModel,
        @Size(max = 255) String visionModel) {
}
