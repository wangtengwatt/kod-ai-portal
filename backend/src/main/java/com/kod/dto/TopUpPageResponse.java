package com.kod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 充值记录分页响应。
 */
@Data
@AllArgsConstructor
public class TopUpPageResponse {

    @JsonProperty("items")
    private List<TopUpItemResponse> items;

    @JsonProperty("total")
    private long total;

    @JsonProperty("page")
    private int page;

    @JsonProperty("page_size")
    private int pageSize;
}
