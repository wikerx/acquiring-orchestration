package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 受管 Redis Key 分页响应。
 */
@Data
public class CacheKeyPageResponse {

    /**
     * 当前页的 Key 元数据。
     */
    private List<CacheKeyResponse> records = new ArrayList<>();

    /**
     * 本次有界扫描发现的 Key 总数。
     */
    private Integer total;

    /**
     * 是否因达到扫描上限而截断结果。
     */
    private Boolean truncated;
}
