package com.scott.payment.settlement.service;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ExchangeRateUsageMessage;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import com.scott.payment.settlement.mapper.SettlementEventOutboxMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 结算汇率实际使用 Outbox 构造与持久化服务。
 *
 * <p>只在结算结果已经完成计算和回读校验的事务中调用。每个跨币种批次汇率行生成一个稳定事件，
 * 同币种 SYSTEM_IDENTITY 行不属于外汇使用事实并明确跳过。</p>
 */
@Service
public class SettlementRateUsageOutboxService {

    private static final String EVENT_PREFIX = "FXST:";

    private static final String PRODUCER_SERVICE = "service-settlement";

    private final SettlementEventOutboxMapper outboxMapper;

    /**
     * 创建结算汇率使用 Outbox 服务。
     *
     * @param outboxMapper 结算事件 Outbox 数据访问入口
     */
    public SettlementRateUsageOutboxService(SettlementEventOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    /**
     * 为批次内每个真实跨币种汇率追加普通消息 Outbox，并逐条回读验证幂等事实。
     *
     * @param batch 已完成结果计算但尚未推进 CALCULATED 的结算批次
     * @param rates 本批已锁定并实际用于计算的完整汇率矩阵
     * @param appliedTime 结算结果业务应用时间
     * @return 实际跨币种汇率事件数量
     */
    public int saveForCalculatedBatch(SettlementBatchDO batch,
                                      SettlementLockedRateMatrix rates,
                                      LocalDateTime appliedTime) {
        Objects.requireNonNull(batch, "settlement batch is required");
        Objects.requireNonNull(rates, "settlement locked rates are required");
        Objects.requireNonNull(appliedTime, "settlement rate applied time is required");
        int eventCount = 0;
        for (LockedRate rate : rates.matrix().rates()) {
            if (rate.pair().sourceCurrency().equals(rate.pair().targetCurrency())) {
                continue;
            }
            SettlementEventOutboxDO expected = event(batch, rates, rate, appliedTime);
            int affectedRows = outboxMapper.insertRateUsageIdempotent(expected);
            if (affectedRows < 0 || affectedRows > 1) {
                throw new IllegalStateException("settlement rate usage outbox insert affected unexpected rows");
            }
            SettlementEventOutboxDO stored = outboxMapper.selectByEventNo(expected.getEventNo());
            if (!sameFrozenFact(stored, expected)) {
                throw new IllegalStateException(
                        "settlement rate usage event conflicts with a different persisted outbox fact");
            }
            eventCount++;
        }
        return eventCount;
    }

    /**
     * 由结算批次号和 settlement_batch_rate.id 构造稳定事件及非敏感消息快照。
     */
    private SettlementEventOutboxDO event(SettlementBatchDO batch,
                                          SettlementLockedRateMatrix rates,
                                          LockedRate rate,
                                          LocalDateTime appliedTime) {
        long rateUsageId = rates.requireRateId(rate.pair().sourceCurrency());
        long businessRateId = parseBusinessRateId(rate.quoteId());
        String eventNo = EVENT_PREFIX + batch.getSettlementBatchNo() + ":" + rateUsageId;
        if (eventNo.length() > 96) {
            throw new IllegalStateException("settlement rate usage event number exceeds outbox capacity");
        }

        ExchangeRateUsageMessage message = new ExchangeRateUsageMessage();
        message.setMessageId(eventNo);
        message.setUsageEventId(eventNo);
        message.setCreatedAt(appliedTime);
        message.setTraceId(batch.getSettlementBatchNo());
        message.setRetryCount(0);
        message.setProducerService(PRODUCER_SERVICE);
        message.setRateType("SETTLEMENT_RATE");
        message.setUsageScene("SETTLEMENT");
        message.setBusinessType("SETTLEMENT");
        message.setBusinessNo(batch.getSettlementBatchNo());
        message.setBaseCurrency(rate.pair().sourceCurrency());
        message.setQuoteCurrency(rate.pair().targetCurrency());
        message.setUsedRate(rate.directRate());
        message.setBusinessRateId(businessRateId);
        message.setRateSource(rate.rateSource());
        message.setSourceQuoteDirection(rate.sourceQuoteDirection().name());
        message.setRateEffectiveTime(rate.effectiveTime());
        message.setCalculationDescription(
                "settlementAmount = sourceAmount * lockedDirectRate; settlementBatchRateId="
                        + rateUsageId);
        message.setAppliedTime(appliedTime);

        SettlementEventOutboxDO row = new SettlementEventOutboxDO();
        row.setEventNo(eventNo);
        row.setSettlementBatchNo(batch.getSettlementBatchNo());
        row.setRateUsageId(rateUsageId);
        row.setTopic(MqTopic.EXCHANGE_RATE_USAGE);
        row.setTag(MqTag.EXCHANGE_RATE_USAGE);
        row.setMessageKey(eventNo);
        // 兼容迁移前 message_group 非空约束；普通消息发布分支不会使用该值做 FIFO 路由。
        row.setMessageGroup(eventNo);
        row.setPayloadJson(JsonUtils.toJsonString(message));
        row.setEventStatus("INIT");
        row.setRetryCount(0);
        row.setNextRetryTime(appliedTime);
        row.setVersion(0L);
        row.setCreateTime(appliedTime);
        row.setUpdateTime(appliedTime);
        return row;
    }

    /**
     * 将锁定报价号解析为 exchange_business_rate.id，禁止无法追溯来源的结算汇率进入快照。
     */
    private long parseBusinessRateId(String quoteId) {
        try {
            long id = Long.parseLong(quoteId);
            if (id <= 0) {
                throw new NumberFormatException("non-positive business rate id");
            }
            return id;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "settlement locked cross-currency rate has no valid business rate id", exception);
        }
    }

    /**
     * 核对唯一事件号命中的冻结身份和 JSON，防止其他唯一键冲突被误判为幂等成功。
     */
    private boolean sameFrozenFact(SettlementEventOutboxDO existing,
                                   SettlementEventOutboxDO expected) {
        return existing != null
                && Objects.equals(existing.getEventNo(), expected.getEventNo())
                && Objects.equals(existing.getSettlementBatchNo(), expected.getSettlementBatchNo())
                && Objects.equals(existing.getCandidateId(), expected.getCandidateId())
                && Objects.equals(existing.getRateUsageId(), expected.getRateUsageId())
                && Objects.equals(existing.getTopic(), expected.getTopic())
                && Objects.equals(existing.getTag(), expected.getTag())
                && Objects.equals(existing.getMessageKey(), expected.getMessageKey())
                && sameJsonPayload(existing.getPayloadJson(), expected.getPayloadJson());
    }

    /**
     * 按 JSON 结构核对冻结消息，兼容 MySQL JSON 列回读时增加空白等规范化行为。
     */
    private boolean sameJsonPayload(String existingPayload, String expectedPayload) {
        try {
            Object existingJson = JsonUtils.parseObject(existingPayload, Object.class);
            Object expectedJson = JsonUtils.parseObject(expectedPayload, Object.class);
            return existingJson != null && sameJsonValue(existingJson, expectedJson);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 递归核对 JSON 结构；数字按数学值比较，以兼容 MySQL JSON 对无意义尾随零的规范化。
     */
    private boolean sameJsonValue(Object existing, Object expected) {
        if (existing == expected) {
            return true;
        }
        if (existing == null || expected == null) {
            return false;
        }
        if (existing instanceof Number existingNumber && expected instanceof Number expectedNumber) {
            return sameJsonNumber(existingNumber, expectedNumber);
        }
        if (existing instanceof Map<?, ?> existingMap && expected instanceof Map<?, ?> expectedMap) {
            if (!existingMap.keySet().equals(expectedMap.keySet())) {
                return false;
            }
            for (Object key : existingMap.keySet()) {
                if (!sameJsonValue(existingMap.get(key), expectedMap.get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (existing instanceof List<?> existingList && expected instanceof List<?> expectedList) {
            if (existingList.size() != expectedList.size()) {
                return false;
            }
            for (int index = 0; index < existingList.size(); index++) {
                if (!sameJsonValue(existingList.get(index), expectedList.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(existing, expected);
    }

    /**
     * JSON 只有统一的 number 类型；数据库改变数字表示形式时不得改变其业务数值语义。
     */
    private boolean sameJsonNumber(Number existing, Number expected) {
        return new BigDecimal(existing.toString()).compareTo(new BigDecimal(expected.toString())) == 0;
    }
}
