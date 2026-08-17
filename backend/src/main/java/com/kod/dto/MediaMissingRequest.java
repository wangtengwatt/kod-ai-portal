package com.kod.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MediaMissingRequest(
        @NotEmpty @Size(max = 200) List<@Size(min = 1, max = 255) String> keys
) {
}
