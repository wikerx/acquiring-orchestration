package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.CacheGenerationChangedMessage;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinCacheGenerationConsumer
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : Card BIN generation 普通消息消费者，在管理端提交后即时切换失败时通过可靠 MQ 完成最终补偿。
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "payment.card-bin.cache",
        name = "invalidation-mq-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.CACHE_INVALIDATION,
        consumerGroup = "service-payment-card-bin-cache",
        selectorExpression = MqTag.CARD_BIN_CACHE_CHANGED,
        messageModel = MessageModel.CLUSTERING)
public class CardBinCacheGenerationConsumer implements RocketMQListener<String> {

    /**
     * {@code CACHE_NAMESPACE}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CACHE_NAMESPACE = "card-bin-range";
    /**
     * {@code RECOVERY_GATE_TTL}常量，统一 {@code CardBinCacheGenerationConsumer} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final Duration RECOVERY_GATE_TTL = Duration.ofMinutes(30);

    private final RedisCacheGenerationStore generationStore;

    /** @param generationStore Redis generation 原子存储 */
    public CardBinCacheGenerationConsumer(RedisCacheGenerationStore generationStore) {
        this.generationStore = generationStore;
    }

    /** 消费重复消息时 commit 保持幂等；原门禁过期时切换一个新的安全 generation。 */
    @Override
    public void onMessage(String payload) {
        CacheGenerationChangedMessage message = JsonUtils.parseObject(payload, CacheGenerationChangedMessage.class);
        if (!valid(message)) {
            throw new IllegalArgumentException("Card BIN cache generation message is invalid");
        }
        RedisCachePublication publication = new RedisCachePublication(
                message.getNamespace(), message.getPublicationToken(), message.getGeneration());
        if (generationStore.commit(publication)) {
            return;
        }
        RedisCachePublication replacement = generationStore.begin(CACHE_NAMESPACE, RECOVERY_GATE_TTL);
        if (!generationStore.commit(replacement)) {
            generationStore.abort(replacement);
            throw new IllegalStateException("Card BIN cache recovery generation commit failed");
        }
        log.warn("event: CARD_BIN_CACHE_GENERATION_RECOVERED originalGeneration: {}",
                message.getGeneration());
    }

    private boolean valid(CacheGenerationChangedMessage message) {
        return message != null
                && CACHE_NAMESPACE.equals(message.getNamespace())
                && MqTag.CARD_BIN_CACHE_CHANGED.equals(message.getEventType())
                && StringUtils.hasText(message.getMessageId())
                && StringUtils.hasText(message.getPublicationToken())
                && StringUtils.hasText(message.getGeneration());
    }
}
