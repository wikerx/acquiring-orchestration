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

/** 默认清分异常案件服务，复用交易异常逻辑表并使用确定性去重键。 */
@Service
public class DefaultClearingAnomalyService implements ClearingAnomalyService {

    private static final int SUMMARY_MAX_LENGTH = 512;

    private final ClearingTransactionAbnormalEventMapper mapper;
    private final ClearingOperationalMetrics metrics;

    public DefaultClearingAnomalyService(ClearingTransactionAbnormalEventMapper mapper,
                                         ClearingOperationalMetrics metrics) {
        this.mapper = mapper;
        this.metrics = metrics;
    }

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

    @Override
    public void resolve(String transactionId, LocalDateTime transactionDateTime,
                        String referenceId, LocalDateTime now) {
        if (!StringUtils.hasText(transactionId) || transactionDateTime == null
                || !StringUtils.hasText(referenceId) || now == null) {
            throw new IllegalArgumentException("clearing anomaly resolution identity and time are required");
        }
        mapper.resolveActiveClearingCases(transactionId, transactionDateTime, referenceId, now);
    }

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

    private String level(ClearingAnomalyTypeEnum type) {
        return switch (type) {
            case FINANCIAL_MISMATCH -> "CRITICAL";
            case MANUAL_REVIEW -> "HIGH";
            case CONTROLLED_FAILURE, PROJECTION_MISMATCH -> "MEDIUM";
        };
    }

    private String sanitize(String value) {
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= SUMMARY_MAX_LENGTH
                ? normalized : normalized.substring(0, SUMMARY_MAX_LENGTH);
    }

    private String hash(String material) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
