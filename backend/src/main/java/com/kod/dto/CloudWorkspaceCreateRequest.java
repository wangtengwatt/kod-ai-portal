package com.kod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloudWorkspaceCreateRequest(@NotBlank @Size(max = 512) String workingDirectory) {
}
