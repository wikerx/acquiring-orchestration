package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ExchangeRateUsageMessage;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 支付交易汇率实际使用事件构造支持。
 *
 * <p>首次交易与使用首笔冻结汇率的后续动作共享同一事件契约。调用方必须在交易事实所属事务中
 * 保存事件，使汇率使用审计与交易金额快照原子提交；本类不查询数据库，也不解释交易状态。</p>
 */
final class TransactionRateUsageOutboxSupport {

    private static final String EVENT_PREFIX = "FXTR:";

    private static final String AGGREGATE_TYPE = "EXCHANGE_RATE_USAGE";

    private static final String PRODUCER_SERVICE = "service-payment";

    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    private static final int DEFAULT_MAX_RETRY_COUNT = 200;

    private TransactionRateUsageOutboxSupport() {
    }

    /**
     * 将一笔已经实际参与跨币种金额计算的交易汇率写入普通消息 Outbox。
     *
     * @param outboxService 交易 Outbox 持久化服务
     * @param commandDTO 已完成金额换算的内部交易命令
     * @param resultDTO 已分配稳定交易号的交易结果
     * @param rate 实际采用且可追溯的业务汇率版本
     * @param appliedTime 汇率参与本次金额计算的业务时间
     * @param calculationDescription 不含敏感数据的计算口径说明
     */
    static void save(TransactionEventOutboxService outboxService,
                     PaymentCreateCommandDTO commandDTO,
                     PaymentCreateResultDTO resultDTO,
                     PaymentExchangeRateDTO rate,
                     LocalDateTime appliedTime,
                     String calculationDescription) {
        validate(outboxService, commandDTO, resultDTO, rate, appliedTime);
        String eventNo = EVENT_PREFIX + resultDTO.getTransactionId();
        if (eventNo.length() > 64) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "transaction rate usage event number exceeds outbox capacity");
        }

        ExchangeRateUsageMessage message = message(
                eventNo, commandDTO, resultDTO, rate, appliedTime, calculationDescription);
        outboxService.save(event(eventNo, commandDTO, resultDTO, message, appliedTime));
    }

    /**
     * 构造跨服务汇率使用消息，保留业务汇率版本和真实交易类型。
     */
    private static ExchangeRateUsageMessage message(String eventNo,
                                                    PaymentCreateCommandDTO commandDTO,
                                                    PaymentCreateResultDTO resultDTO,
                                                    PaymentExchangeRateDTO rate,
                                                    LocalDateTime appliedTime,
                                                    String calculationDescription) {
        ExchangeRateUsageMessage message = new ExchangeRateUsageMessage();
        message.setMessageId(eventNo);
        message.setUsageEventId(eventNo);
        message.setCreatedAt(appliedTime);
        message.setTraceId(TraceContext.getTraceId());
        message.setRetryCount(0);
        message.setProducerService(PRODUCER_SERVICE);
        message.setRateType("TRANSACTION_RATE");
        message.setUsageScene("TRANSACTION");
        message.setBusinessType(resultDTO.getTransactionType());
        message.setBusinessNo(resultDTO.getTransactionId());
        message.setBaseCurrency(rate.getBaseCurrency());
        message.setQuoteCurrency(rate.getQuoteCurrency());
        message.setUsedRate(rate.getFinalRate());
        message.setBusinessRateId(rate.getRateId());
        message.setRawRateId(rate.getRawRateId());
        message.setRuleId(rate.getRuleId());
        message.setRateSource(rate.getSourceCode());
        // exchange_business_rate.final_rate 已归一为 base -> quote 的直接汇率。
        message.setSourceQuoteDirection("DIRECT");
        message.setRateEffectiveTime(rate.getEffectiveTime());
        message.setCalculationDescription(calculationDescription);
        message.setAppliedTime(appliedTime);
        return message;
    }

    /**
     * 构造与交易事实同分片的普通消息 Outbox 记录。
     */
    private static TransactionEventOutboxDO event(String eventNo,
                                                  PaymentCreateCommandDTO commandDTO,
                                                  PaymentCreateResultDTO resultDTO,
                                                  ExchangeRateUsageMessage message,
                                                  LocalDateTime appliedTime) {
        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setEventNo(eventNo);
        eventDO.setAggregateType(AGGREGATE_TYPE);
        eventDO.setAggregateNo(resultDTO.getTransactionId());
        eventDO.setTransactionId(resultDTO.getTransactionId());
        eventDO.setOperationId(resultDTO.getOperationId());
        eventDO.setMerchantId(commandDTO.getMerchantId());
        eventDO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        eventDO.setTransactionType(resultDTO.getTransactionType());
        eventDO.setEventType(MqTag.EXCHANGE_RATE_USAGE);
        eventDO.setEventStatus("INIT");
        eventDO.setTopic(MqTopic.EXCHANGE_RATE_USAGE);
        eventDO.setTag(MqTag.EXCHANGE_RATE_USAGE);
        eventDO.setMessageKey(eventNo);
        eventDO.setDeliveryMode("NORMAL");
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(appliedTime);
        eventDO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        eventDO.setTransactionUtcTime(toUtcTime(commandDTO.getTransactionDateTime()));
        eventDO.setTransactionTimeZone(DEFAULT_TIME_ZONE);
        eventDO.setRetryCount(0);
        eventDO.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        eventDO.setNextRetryTime(appliedTime);
        eventDO.setVersion(0);
        eventDO.setDeleted(0);
        eventDO.setCreateTime(appliedTime);
        eventDO.setUpdateTime(appliedTime);
        return eventDO;
    }

    /**
     * 校验生产者传入的汇率身份和币种方向，禁止不可追溯或同币种事件进入 Outbox。
     */
    private static void validate(TransactionEventOutboxService outboxService,
                                 PaymentCreateCommandDTO commandDTO,
                                 PaymentCreateResultDTO resultDTO,
                                 PaymentExchangeRateDTO rate,
                                 LocalDateTime appliedTime) {
        Objects.requireNonNull(outboxService, "transaction event outbox service is required");
        Objects.requireNonNull(commandDTO, "payment command is required");
        Objects.requireNonNull(resultDTO, "payment result is required");
        Objects.requireNonNull(rate, "applied exchange rate is required");
        Objects.requireNonNull(appliedTime, "exchange rate applied time is required");
        BigDecimal usedRate = rate.getFinalRate();
        if (!StringUtils.hasText(resultDTO.getTransactionId())
                || !StringUtils.hasText(resultDTO.getTransactionType())
                || commandDTO.getTransactionDateTime() == null
                || rate.getRateId() == null || rate.getRateId() <= 0
                || !StringUtils.hasText(rate.getBaseCurrency())
                || !StringUtils.hasText(rate.getQuoteCurrency())
                || rate.getBaseCurrency().equals(rate.getQuoteCurrency())
                || usedRate == null || usedRate.signum() <= 0
                || !StringUtils.hasText(rate.getSourceCode())
                || rate.getEffectiveTime() == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "applied transaction rate is incomplete");
        }
    }

    /**
     * 按平台业务时区将交易时间转换为 UTC 分片审计时间。
     */
    private static LocalDateTime toUtcTime(LocalDateTime transactionDateTime) {
        return transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
