package com.kod.dto;

import java.util.List;

public record SyncExchangeResponse(
        long cursor,
        List<String> acknowledgedMutationIds,
        List<SyncChangeResponse> changes) {
}
