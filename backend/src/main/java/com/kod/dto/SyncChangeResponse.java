package com.kod.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SyncChangeResponse(
        long cursor,
        String entityType,
        String entityId,
        long revision,
        long clientUpdatedAt,
        long serverUpdatedAt,
        String sourceDeviceId,
        boolean deleted,
        boolean sensitive,
        JsonNode payload) {
}
