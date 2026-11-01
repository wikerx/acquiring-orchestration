package com.scott.payment.admin.dto.monitor;

import lombok.Data;

/**
 * 受管 Redis Key 元数据响应。
 *
 * <p>该模型只暴露类型、TTL 和大小，不读取或返回缓存内容。</p>
 */
@Data
public class CacheKeyResponse {

    /**
     * 受管平台配置缓存的完整物理 Key。
     */
    private String key;

    /**
     * Redis 数据结构类型。
     */
    private String type;

    /**
     * 剩余生存时间，单位为秒；Redis 原生负值语义保持不变。
     */
    private Long ttl;

    /**
     * String 字节长度或集合成员数量。
     */
    private Long size;
}
