package com.kod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中转站列表项。
 */
@Data
@AllArgsConstructor
public class StationItem {

    /** 中转站ID。 */
    private Long id;

    /** 中转站URL。 */
    private String url;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
