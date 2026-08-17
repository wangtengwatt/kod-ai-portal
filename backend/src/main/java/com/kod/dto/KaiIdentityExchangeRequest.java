package com.kod.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KaiIdentityExchangeRequest {

    @NotBlank(message = "KAI Identity access token must not be blank")
    private String accessToken;
}
