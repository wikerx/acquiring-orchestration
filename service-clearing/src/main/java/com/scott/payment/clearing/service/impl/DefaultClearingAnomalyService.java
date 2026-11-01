package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;
import com.scott.payment.clearing.entity.ClearingTransactionAbnormalEventDO;
import com.scott.payment.clearing.mapper.ClearingTransactionAbnormalEventMapper;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 默认清分异常案件服务，复用交易异常逻辑表并使用确定性去重键。
 */
@Service
public class DefaultClearingAnomalyService implements ClearingAnomalyService {

    /**
     * 异常摘要允许落库的最大字符数，避免排障文本超过异常表字段容量。
     */
    private static final int SUMMARY_MAX_LENGTH = 512;

    private final ClearingTransactionAbnormalEventMapper mapper;
    private final ClearingOperationalMetrics metrics;

    /**
     * 创建清分异常服务，并注入异常持久化与监控指标依赖。
     */
    public DefaultClearingAnomalyService(ClearingTransactionAbnormalEventMapper mapper,
                                         ClearingOperationalMetrics metrics) {
        this.mapper = mapper;
        this.metrics = metrics;
    }

    /**
     * 记录清分异常，使用确定性摘要键保证同一异常重复上报时只累计出现次数。
     * @param operation 当前交易动作事实，包含交易身份、状态、版本和分片定位信息
     * @param financeStateId 业务记录主键或主键集合，用于精确定位当前操作对象
     * @param revision 清分修订号，用于区分同一动作的不可变清分版本
     * @param anomalyType 清分异常类型编码，用于聚合告警和人工处置
     * @param failureCode 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param summary 不含敏感数据的异常或处理摘要，用于运营排障
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     */
    @Override
    public void record(ClearingOperationFacts operation, String financeStateId, int revision,
                       ClearingAnomalyTypeEnum anomalyType, String failureCode,
                       String summary, LocalDateTime now) {
        requireRecord(operation, financeStateId, revision, anomalyType, failureCode, summary, now);
        String deduplicationKey = hash(financeStateId + "|" + revision + "|"
                + anomalyType.name() + "|" + failureCode);
        ClearingTransactionAbnormalEventDO row = new ClearingTransactionAbnormalEventDO();
        row.setAbnormalEventId("CA" + deduplicationKey.substring(0, 32));
        row.setTransactionId(operation.transactionId());
        row.setOperationId(operation.operationId());
        row.setAbnormalType("CLEARING_" + anomalyType.name());
        row.setAbnormalLevel(level(anomalyType));
        row.setEventStatus("OPEN");
        row.setSourceRecordType("CLEARING_FINANCE_STATE");
        row.setSourceRecordId(financeStateId + ":" + revision);
        row.setAbnormalDescription(sanitize(summary));
        row.setRawReferenceJson(null);
        row.setFirstSeenTime(now);
        row.setTransactionDateTime(operation.transactionDateTime());
        row.setTransactionUtcTime(operation.transactionUtcTime());
        row.setTransactionTimeZone(operation.transactionTimeZone());
        row.setDeduplicationKey(deduplicationKey);
        row.setMerchantId(operation.merchantId());
        row.setMerchantOrderNo(operation.merchantOrderNo());
        row.setSourceTransactionId(operation.sourceTransactionId());
        row.setTransactionType(operation.transactionType());
        row.setPlatformStatus(failureCode);
        row.setChannelMatchResult("NOT_APPLICABLE");
        row.setDetectSource("CLEARING_SERVICE");
        row.setLastSeenTime(now);
        row.setOccurrenceCount(1);
        row.setMerchantNotifyRequired(0);
        row.setVersion(0);
        row.setDeleted(0);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        if (mapper.upsertOccurrence(row) < 1) {
            throw new IllegalStateException("clearing anomaly upsert did not affect a row");
        }
        metrics.recordAnomaly(anomalyType);
        if (anomalyType == ClearingAnomalyTypeEnum.FINANCIAL_MISMATCH) {
            metrics.recordAmountImbalance(operation.labelCurrency());
        }
    }

    /**
     * 关闭指定交易分片上仍处于开启状态的清分异常，并记录解决引用号。
     */
    @Override
    public void resolve(String transactionId, LocalDateTime transactionDateTime,
                        String referenceId, LocalDateTime now) {
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null
                || !StringUtils.hasText(referenceId) || now == null) {
            throw new IllegalArgumentException("clearing anomaly resolution identity and time are required");
        }
        mapper.resolveActiveClearingCases(transactionId, transactionDateTime, referenceId, now);
    }

    /**
     * 校验异常记录所需的交易身份、财务状态版本和安全摘要均完整有效。
     */
    private void requireRecord(ClearingOperationFacts operation, String financeStateId, int revision,
                               ClearingAnomalyTypeEnum anomalyType, String failureCode,
                               String summary, LocalDateTime now) {
        if (operation == null || !StringUtils.hasText(operation.transactionId())
                || !StringUtils.hasText(operation.operationId()) || operation.transactionDateTime() == null
                || !StringUtils.hasText(financeStateId) || revision < 0 || anomalyType == null
                || !StringUtils.hasText(failureCode) || !StringUtils.hasText(summary) || now == null) {
            throw new IllegalArgumentException("complete clearing anomaly facts are required");
        }
    }

    /**
     * 将受控异常类型映射为运营告警级别。
     */
    private String level(ClearingAnomalyTypeEnum type) {
        return switch (type) {
            case FINANCIAL_MISMATCH -> "CRITICAL";
            case MANUAL_REVIEW -> "HIGH";
            case CONTROLLED_FAILURE, PROJECTION_MISMATCH -> "MEDIUM";
        };
    }

    /**
     * 移除摘要中的控制字符并按数据库容量截断，避免日志注入和字段溢出。
     */
    private String sanitize(String value) {
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= SUMMARY_MAX_LENGTH
                ? normalized : normalized.substring(0, SUMMARY_MAX_LENGTH);
    }

    /**
     * 对稳定异常材料计算 SHA-256 摘要，作为异常记录的去重依据。
     */
    private String hash(String material) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
