package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 跨服务缓存 generation 变更消息，仅承载受控命名空间和 Redis 发布凭证，不包含缓存值或业务敏感字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CacheGenerationChangedMessage extends BaseMqMessage {

    private static final long serialVersionUID = 1L;

    /**
     * 受控缓存命名空间。
     */
    private String namespace;

    /**
     * generation 门禁持有令牌。
     */
    private String publicationToken;

    /**
     * 数据库变更对应的新 generation。
     */
    private String generation;

    /**
     * 必须与 RocketMQ Tag 一致的事件类型。
     */
    private String eventType;
}
