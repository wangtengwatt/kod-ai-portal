package com.kod.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreKitTransactionRequest(
        @NotBlank @Size(max = 32768) String signedTransaction) {
}
