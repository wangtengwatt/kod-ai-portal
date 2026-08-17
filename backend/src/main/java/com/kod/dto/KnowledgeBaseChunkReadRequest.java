package com.kod.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnowledgeBaseChunkReadRequest(
        @NotEmpty @Size(max = 100) List<@Valid ChunkRef> chunks) {
    public record ChunkRef(@Min(1) long fileId, @Min(0) @Max(1000000) int chunkIndex) { }
}
