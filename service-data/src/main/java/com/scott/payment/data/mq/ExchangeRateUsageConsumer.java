package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ExchangeRateUsageMessage;
import com.scott.payment.data.service.ExchangeRateUsagePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Payment 与 Settlement 汇率实际使用事实消费者。
 *
 * <p>消费者拒绝查询报价、同币种恒等率、类型与生产服务不一致或超出数据库精度的消息；
 * 最终重复判断由 exchange_rate_usage_snapshot.usage_event_id 唯一键及完整事实回读完成。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.exchange-rate-usage.mq", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.EXCHANGE_RATE_USAGE,
        consumerGroup = DataMqConsumerGroups.EXCHANGE_RATE_USAGE,
        selectorExpression = MqTag.EXCHANGE_RATE_USAGE,
        messageModel = MessageModel.CLUSTERING
)
public class ExchangeRateUsageConsumer implements RocketMQListener<String> {

    private static final Set<String> QUOTE_DIRECTIONS = Set.of("DIRECT", "INVERSE");

    private static final Set<String> TRANSACTION_BUSINESS_TYPES = Set.of(
            "PAYMENT",
            "AUTHORIZATION",
            "PRE_AUTHORIZATION",
            "CAPTURE",
            "PRE_AUTH_COMPLETION",
            "INCREMENTAL_AUTHORIZATION",
            "REFUND"
    );

    private static final Map<String, UsageContract> CONTRACTS = Map.of(
            "TRANSACTION_RATE", new UsageContract("TRANSACTION", "service-payment"),
            "SETTLEMENT_RATE", new UsageContract("SETTLEMENT", "service-settlement")
    );

    private final ExchangeRateUsagePersistenceService persistenceService;

    /**
     * 创建汇率使用事实消费者。
     *
     * @param persistenceService 汇率使用事实事务落库服务
     */
    public ExchangeRateUsageConsumer(ExchangeRateUsagePersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * 解析、校验并持久化一条汇率实际使用消息。
     *
     * @param payload RocketMQ JSON 消息体；日志只记录长度和稳定业务标识，不打印原文
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        ExchangeRateUsageMessage message = parse(payload);
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            validate(message);
            boolean inserted = persistenceService.persist(message);
            log.info("汇率使用事实消费完成, event: DATA_EXCHANGE_RATE_USAGE_CONSUMED, traceId: {}, usageEventId: {}, producerService: {}, rateType: {}, usageScene: {}, businessType: {}, businessNo: {}, baseCurrency: {}, quoteCurrency: {}, inserted: {}, durationMs: {}",
                    TraceContext.getTraceId(), message.getUsageEventId(), message.getProducerService(),
                    message.getRateType(), message.getUsageScene(), message.getBusinessType(),
                    message.getBusinessNo(), message.getBaseCurrency(), message.getQuoteCurrency(),
                    inserted, elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 解析 JSON 消息，畸形载荷只记录长度和异常类型，防止原始消息进入日志。
     *
     * @param payload RocketMQ JSON 消息体
     * @return 解析后的公共消息契约
     */
    private ExchangeRateUsageMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("exchange rate usage payload is empty");
        }
        try {
            ExchangeRateUsageMessage message = JsonUtils.parseObject(payload, ExchangeRateUsageMessage.class);
            if (message == null) {
                throw new IllegalArgumentException("exchange rate usage payload is invalid");
            }
            return message;
        } catch (RuntimeException exception) {
            log.error("汇率使用消息解析失败, event: DATA_EXCHANGE_RATE_USAGE_DESERIALIZE_FAILED, payloadLength: {}, exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("exchange rate usage payload is invalid", exception);
        }
    }

    /**
     * 校验消息身份、场景对应关系、币种方向、汇率列容量和审计来源字段。
     *
     * @param message 已反序列化消息
     */
    private void validate(ExchangeRateUsageMessage message) {
        requireText(message.getMessageId(), 96, "messageId");
        requireText(message.getUsageEventId(), 96, "usageEventId");
        if (!message.getMessageId().equals(message.getUsageEventId())) {
            throw invalid("messageId and usageEventId must match");
        }
        UsageContract contract = CONTRACTS.get(message.getRateType());
        if (contract == null
                || !contract.usageScene().equals(message.getUsageScene())
                || !contract.producerService().equals(message.getProducerService())
                || !validBusinessType(message)) {
            throw invalid("rate type, scene, business type and producer service are inconsistent");
        }
        requireText(message.getBusinessNo(), 128, "businessNo");
        requireCurrency(message.getBaseCurrency(), "baseCurrency");
        requireCurrency(message.getQuoteCurrency(), "quoteCurrency");
        if (message.getBaseCurrency().equals(message.getQuoteCurrency())) {
            throw invalid("same-currency identity rate is not an exchange-rate usage fact");
        }
        requireRate(message.getUsedRate());
        requirePositiveId(message.getBusinessRateId(), "businessRateId", false);
        requirePositiveId(message.getRawRateId(), "rawRateId", true);
        requirePositiveId(message.getRuleId(), "ruleId", true);
        requireText(message.getRateSource(), 64, "rateSource");
        if (!QUOTE_DIRECTIONS.contains(message.getSourceQuoteDirection())) {
            throw invalid("sourceQuoteDirection is unsupported");
        }
        if (message.getRateEffectiveTime() == null || message.getAppliedTime() == null
                || message.getCreatedAt() == null) {
            throw invalid("rateEffectiveTime, appliedTime and createdAt are required");
        }
        if (message.getAppliedTime().isBefore(message.getRateEffectiveTime())) {
            throw invalid("appliedTime must not be before rateEffectiveTime");
        }
        if (message.getCalculationDescription() != null
                && message.getCalculationDescription().length() > 512) {
            throw invalid("calculationDescription exceeds 512 characters");
        }
    }

    /**
     * 校验非空文本及其数据库列长度，并拒绝会造成身份漂移的首尾空白。
     */
    private void requireText(String value, int maxLength, String fieldName) {
        if (!StringUtils.hasText(value) || !value.equals(value.trim()) || value.length() > maxLength) {
            throw invalid(fieldName + " is invalid");
        }
    }

    /**
     * 校验业务动作是否属于当前汇率类型允许形成快照的真实资金计算场景。
     */
    private boolean validBusinessType(ExchangeRateUsageMessage message) {
        if ("SETTLEMENT_RATE".equals(message.getRateType())) {
            return "SETTLEMENT".equals(message.getBusinessType());
        }
        return TRANSACTION_BUSINESS_TYPES.contains(message.getBusinessType());
    }

    /**
     * 校验 ISO 4217 三位大写币种代码。
     */
    private void requireCurrency(String value, String fieldName) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw invalid(fieldName + " must be a three-letter uppercase currency code");
        }
    }

    /**
     * 校验 DECIMAL(24,12) 正汇率容量，不允许生产者依赖数据库静默舍入或截断。
     */
    private void requireRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0 || rate.scale() > 12
                || rate.precision() - rate.scale() > 12) {
            throw invalid("usedRate exceeds DECIMAL(24,12) positive-rate capacity");
        }
    }

    /**
     * 校验数据库来源主键；必填字段为空或任意字段非正时拒绝消息。
     */
    private void requirePositiveId(Long value, String fieldName, boolean nullable) {
        if ((!nullable && value == null) || (value != null && value <= 0)) {
            throw invalid(fieldName + " is invalid");
        }
    }

    /**
     * 创建不携带消息原文的稳定参数异常。
     */
    private IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("exchange rate usage message is invalid: " + reason);
    }

    /**
     * 计算单条消息消费耗时，使用单调时钟避免系统时间校准影响。
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 固定汇率类型对应的业务场景、业务域和唯一合法生产服务。
     */
    private record UsageContract(String usageScene, String producerService) {
    }
}
