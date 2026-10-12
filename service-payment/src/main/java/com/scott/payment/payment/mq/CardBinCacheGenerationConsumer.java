package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxReplicaProbe;
import com.scott.payment.component.db.reference.cache.CardBinRangeIndex;
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
 * @description : Card BIN 分片索引发布消费者，确认管理端事务已复制到 SLAVE 后重建永久缓存并提交 generation
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

    /** Card BIN 数据库有界三位分片索引发布器。 */
    private final CardBinRangeIndex rangeIndex;

    /** 通过可靠 MQ Outbox 事件确认管理端事务已经复制到 SLAVE。 */
    private final ReliableMqOutboxReplicaProbe replicaProbe;

    /**
     * @param generationStore Redis generation 原子存储
     * @param rangeIndex Card BIN 三位分片索引发布器
     * @param replicaProbe 可靠 MQ Outbox 从库探针
     */
    public CardBinCacheGenerationConsumer(RedisCacheGenerationStore generationStore,
                                          CardBinRangeIndex rangeIndex,
                                          ReliableMqOutboxReplicaProbe replicaProbe) {
        this.generationStore = generationStore;
        this.rangeIndex = rangeIndex;
        this.replicaProbe = replicaProbe;
    }

    /**
     * 发布从库快照并提交代际。
     *
     * <p>消息事件与 Card BIN 写入处于同一个主库事务；事件在 SLAVE 可见前抛出异常交给
     * RocketMQ 重试。快照完整构建并原子激活后才提交 generation，避免读流量看到半成品。</p>
     */
    @Override
    public void onMessage(String payload) {
        CacheGenerationChangedMessage message = JsonUtils.parseObject(payload, CacheGenerationChangedMessage.class);
        if (!valid(message)) {
            throw new IllegalArgumentException("Card BIN cache generation message is invalid");
        }
        if (!replicaProbe.isVisible(message.getMessageId())) {
            throw new IllegalStateException("Card BIN change is not visible on replica yet");
        }
        RedisCachePublication publication = new RedisCachePublication(
                message.getNamespace(), message.getPublicationToken(), message.getGeneration());
        // 目标 generation 完整写入全部分片后才能提交门禁，查询端不会读取半成品。
        rangeIndex.publishFromReplica(publication.generation());
        if (generationStore.commit(publication)) {
            return;
        }
        // 门禁异常过期后必须按新 generation 再次发布，禁止复用 generation 不匹配的分片。
        RedisCachePublication replacement = generationStore.begin(CACHE_NAMESPACE, RECOVERY_GATE_TTL);
        rangeIndex.publishFromReplica(replacement.generation());
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
