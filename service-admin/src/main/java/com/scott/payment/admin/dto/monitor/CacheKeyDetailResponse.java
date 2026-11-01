package com.scott.payment.admin.dto.monitor;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 单个受管 Redis Key 的详情响应。
 *
 * <p>详情接口显式声明缓存值不可读，并始终返回空值，防止管理端接口成为敏感缓存内容读取入口。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CacheKeyDetailResponse extends CacheKeyResponse {

    /**
     * 管理端是否允许读取缓存值，固定为 {@code false}。
     */
    private Boolean valueReadable;

    /**
     * 缓存值占位字段，固定为 {@code null} 以保持既有接口契约。
     */
    private Object value;
}
