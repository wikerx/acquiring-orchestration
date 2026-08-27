package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecalculateRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRetryRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingReviewRequest;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingManagementCommandService;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.clearing.service.ClearingRecalculationService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** 清分人工命令默认实现；浏览器不能传任意状态，所有状态推进均由固定命令决定。 */
@Service
@Slf4j
public class DefaultClearingManagementCommandService implements ClearingManagementCommandService {

    private static final int OUTBOX_MAX_RETRY_COUNT = 10;
    private static final int REASON_MAX_LENGTH = 512;
    private static final int OPERATOR_MAX_LENGTH = 64;
    private static final Set<String> RETRYABLE = Set.of("PENDING", "FAILED", "WAITING_SOURCE", "MANUAL_REVIEW");

    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    private final ClearingTransactionOperationMapper operationMapper;
    private final ClearingTransactionEventOutboxMapper outboxMapper;
    private final ClearingProjectionService projectionService;
    private final ClearingRecalculationService recalculationService;
    private final ClearingOperationalMetrics metrics;
    private final ClearingAnomalyService anomalyService;
    private final Clock clock;

    @Autowired
    public DefaultClearingManagementCommandService(ClearingTransactionFinanceStateMapper financeStateMapper,
                                                   ClearingTransactionOperationMapper operationMapper,
                                                   ClearingTransactionEventOutboxMapper outboxMapper,
                                                   ClearingProjectionService projectionService,
                                                   ClearingRecalculationService recalculationService,
                                                   ClearingOperationalMetrics metrics,
                                                   ClearingAnomalyService anomalyService) {
        this(financeStateMapper, operationMapper, outboxMapper, projectionService,
                recalculationService, metrics, anomalyService, Clock.systemUTC());
    }

    DefaultClearingManagementCommandService(ClearingTransactionFinanceStateMapper financeStateMapper,
                                            ClearingTransactionOperationMapper operationMapper,
                                            ClearingTransactionEventOutboxMapper outboxMapper,
                                            ClearingProjectionService projectionService,
                                            ClearingRecalculationService recalculationService,
                                            ClearingOperationalMetrics metrics,
                                            ClearingAnomalyService anomalyService,
                                            Clock clock) {
        this.financeStateMapper = financeStateMapper;
        this.operationMapper = operationMapper;
        this.outboxMapper = outboxMapper;
        this.projectionService = projectionService;
        this.recalculationService = recalculationService;
        this.metrics = metrics;
        this.anomalyService = anomalyService;
        this.clock = clock;
    }

    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClearingCommandResponse retry(String transactionId, ClearingRetryRequest request) {
        validateRetry(transactionId, request);
        LocalDateTime now = LocalDateTime.now(clock);
        ClearingTransactionFinanceStateDO state = lockedState(
                transactionId, request.getTransactionDateTime(), request.getExpectedVersion());
        if (!RETRYABLE.contains(state.getClearingStatus())) {
            throw new IllegalStateException("clearing state does not allow manual retry");
        }
        ClearingTransactionOperationDO operation = requiredOperation(transactionId, request.getTransactionDateTime());
        validateOperation(state, operation);
        int retryCount = value(state.getClearingRetryCount()) + 1;
        LocalDateTime deliverAt = now.plusMinutes(1).truncatedTo(ChronoUnit.MILLIS);
        String auditReason = auditReason(request.getOperator(), request.getReason());
        int affected = financeStateMapper.scheduleManualRetry(
                transactionId, request.getTransactionDateTime(), request.getExpectedVersion(),
                retryCount, deliverAt, auditReason, now);
        requireOne(affected, "manual clearing retry CAS");

        ClearingTransactionEventOutboxDO outbox = retryOutbox(
                state, operation, retryCount, deliverAt, request.getOperator(), now);
        boolean inserted = persistRetryOutbox(outbox);
        updateProjection(operation, ClearingStateEnum.FAILED,
                ClearingFailureCodeEnum.CLEARING_MANUAL_RETRY.name(), now);
        String result = inserted ? "SCHEDULED" : "ALREADY_SCHEDULED";
        metrics.recordCommand("RETRY", result);
        return response(state, "RETRY", "FAILED", result);
    }

    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClearingCommandResponse review(String transactionId, ClearingReviewRequest request) {
        validateReview(transactionId, request);
        LocalDateTime now = LocalDateTime.now(clock);
        ClearingTransactionFinanceStateDO state = lockedState(
                transactionId, request.getTransactionDateTime(), request.getExpectedVersion());
        if (ClearingStateEnum.valueOf(state.getClearingStatus()).isCompletedTerminal()
                || ClearingStateEnum.MANUAL_REVIEW.name().equals(state.getClearingStatus())) {
            throw new IllegalStateException("clearing state does not allow review escalation");
        }
        ClearingTransactionOperationDO operation = requiredOperation(transactionId, request.getTransactionDateTime());
        validateOperation(state, operation);
        String auditReason = auditReason(request.getOperator(), request.getReason());
        int affected = financeStateMapper.markManualReview(
                transactionId, request.getTransactionDateTime(), request.getExpectedVersion(),
                "MANUAL_REVIEW_REQUESTED", auditReason, now);
        requireOne(affected, "manual clearing review CAS");
        anomalyService.record(toFacts(operation), state.getFinanceStateId(), value(state.getClearingRevision()),
                ClearingAnomalyTypeEnum.MANUAL_REVIEW, "MANUAL_REVIEW_REQUESTED", auditReason, now);
        updateProjection(operation, ClearingStateEnum.MANUAL_REVIEW, "MANUAL_REVIEW_REQUESTED", now);
        metrics.recordCommand("REVIEW", "ESCALATED");
        return response(state, "REVIEW", "MANUAL_REVIEW", "ESCALATED");
    }

    @Override
    public ClearingCommandResponse recalculate(String transactionId, ClearingRecalculateRequest request) {
        ClearingCommandResponse response = recalculationService.recalculate(transactionId, request);
        metrics.recordCommand("RECALCULATE", response.getResult());
        return response;
    }

    private ClearingTransactionEventOutboxDO retryOutbox(ClearingTransactionFinanceStateDO state,
                                                          ClearingTransactionOperationDO operation,
                                                          int retryCount,
                                                          LocalDateTime deliverAt,
                                                          String operator,
                                                          LocalDateTime now) {
        int revision = value(state.getClearingRevision());
        String eventNo = deterministicEventNo(state.getFinanceStateId(), revision, retryCount, deliverAt, operator);
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(now);
        message.setRetryCount(0);
        message.setTransactionId(operation.getTransactionId());
        message.setOperationId(operation.getOperationId());
        message.setMerchantId(operation.getMerchantId());
        message.setMerchantOrderNo(operation.getMerchantOrderNo());
        message.setTransactionType(operation.getTransactionType());
        message.setTransactionStatus(operation.getTransactionStatus());
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionDateTime(operation.getTransactionDateTime());
        message.setSourceEventNo("MANUAL:" + state.getFinanceStateId());
        message.setExpectedClearingRevision(revision);
        message.setClearingRetryCount(retryCount);
        message.setRetryReasonCode(ClearingFailureCodeEnum.CLEARING_MANUAL_RETRY.name());
        message.setDeliverAt(deliverAt.toInstant(ZoneOffset.UTC));

        ClearingTransactionEventOutboxDO row = new ClearingTransactionEventOutboxDO();
        row.setEventNo(eventNo);
        row.setAggregateType("TRANSACTION_CLEARING_RETRY");
        row.setAggregateNo(state.getFinanceStateId() + ":" + retryCount);
        row.setTransactionId(operation.getTransactionId());
        row.setOperationId(operation.getOperationId());
        row.setMerchantId(operation.getMerchantId());
        row.setMerchantOrderNo(operation.getMerchantOrderNo());
        row.setTransactionType(operation.getTransactionType());
        row.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        row.setEventStatus("INIT");
        row.setTopic(MqTopic.PAYMENT_CLEARING_DELAY);
        row.setTag(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        row.setMessageKey(eventNo);
        row.setDeliveryMode("SCHEDULED");
        row.setDeliverAt(deliverAt);
        row.setPayloadJson(JsonUtils.toJsonString(message));
        row.setRetryCount(0);
        row.setMaxRetryCount(OUTBOX_MAX_RETRY_COUNT);
        row.setNextRetryTime(now);
        row.setEventTime(now);
        row.setTransactionDateTime(operation.getTransactionDateTime());
        row.setTransactionUtcTime(operation.getTransactionUtcTime());
        row.setTransactionTimeZone(operation.getTransactionTimeZone());
        row.setVersion(0);
        row.setDeleted(0);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /**
     * 在人工命令事务内核对稳定事件身份，防止错误唯一键冲突被当作已调度。
     */
    private boolean persistRetryOutbox(ClearingTransactionEventOutboxDO expected) {
        ClearingTransactionEventOutboxDO existing = outboxMapper.selectByEventNoForUpdate(
                expected.getEventNo(), expected.getTransactionDateTime());
        if (existing != null) {
            if (!sameOutboxIdentity(existing, expected)) {
                throw new IllegalStateException("manual clearing retry outbox identity is inconsistent");
            }
            return false;
        }
        requireOne(outboxMapper.insertLogical(expected), "manual clearing retry outbox insert");
        return true;
    }

    private boolean sameOutboxIdentity(ClearingTransactionEventOutboxDO actual,
                                       ClearingTransactionEventOutboxDO expected) {
        return Objects.equals(actual.getEventNo(), expected.getEventNo())
                && Objects.equals(actual.getAggregateType(), expected.getAggregateType())
                && Objects.equals(actual.getAggregateNo(), expected.getAggregateNo())
                && Objects.equals(actual.getTransactionId(), expected.getTransactionId())
                && Objects.equals(actual.getOperationId(), expected.getOperationId())
                && Objects.equals(actual.getMerchantId(), expected.getMerchantId())
                && Objects.equals(actual.getMerchantOrderNo(), expected.getMerchantOrderNo())
                && Objects.equals(actual.getTransactionType(), expected.getTransactionType())
                && Objects.equals(actual.getEventType(), expected.getEventType())
                && Objects.equals(actual.getTopic(), expected.getTopic())
                && Objects.equals(actual.getTag(), expected.getTag())
                && Objects.equals(actual.getMessageKey(), expected.getMessageKey())
                && Objects.equals(actual.getMessageGroup(), expected.getMessageGroup())
                && Objects.equals(actual.getDeliveryMode(), expected.getDeliveryMode())
                && Objects.equals(actual.getDeliverAt(), expected.getDeliverAt())
                && Objects.equals(actual.getPayloadJson(), expected.getPayloadJson());
    }

    private String deterministicEventNo(String stateId, int revision, int retryCount,
                                        LocalDateTime deliverAt, String operator) {
        String material = stateId + "|" + revision + "|" + retryCount + "|MANUAL|"
                + deliverAt + "|" + operator;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return "CM" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ClearingTransactionFinanceStateDO lockedState(String transactionId,
                                                           LocalDateTime transactionDateTime,
                                                           int expectedVersion) {
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectForUpdate(transactionId, transactionDateTime);
        if (state == null || !Objects.equals(state.getTransactionId(), transactionId)
                || !Objects.equals(state.getTransactionDateTime(), transactionDateTime)
                || !Objects.equals(state.getVersion(), expectedVersion)
                || !"NOT_SETTLED".equals(state.getSettlementStatus())) {
            throw new IllegalStateException("clearing state is missing, stale or already settled");
        }
        return state;
    }

    private ClearingTransactionOperationDO requiredOperation(String transactionId,
                                                             LocalDateTime transactionDateTime) {
        ClearingTransactionOperationDO operation = operationMapper.selectByTransaction(transactionId, transactionDateTime);
        if (operation == null) {
            throw new IllegalStateException("clearing transaction operation is missing");
        }
        return operation;
    }

    private void validateOperation(ClearingTransactionFinanceStateDO state,
                                   ClearingTransactionOperationDO operation) {
        if (!Objects.equals(state.getTransactionId(), operation.getTransactionId())
                || !Objects.equals(state.getOperationId(), operation.getOperationId())
                || !Objects.equals(state.getMerchantId(), operation.getMerchantId())
                || !Objects.equals(state.getTransactionDateTime(), operation.getTransactionDateTime())) {
            throw new IllegalStateException("clearing operation identity is inconsistent");
        }
    }

    private void updateProjection(ClearingTransactionOperationDO operation,
                                  ClearingStateEnum status,
                                  String failureCode,
                                  LocalDateTime now) {
        try {
            projectionService.updateResolvingLocator(toFacts(operation), status, failureCode, now);
        } catch (RuntimeException exception) {
            log.warn("event: CLEARING_MANUAL_PROJECTION_DEFERRED transactionId: {} actionStatus: {}",
                    operation.getTransactionId(), status);
        }
    }

    private ClearingOperationFacts toFacts(ClearingTransactionOperationDO row) {
        return new ClearingOperationFacts(
                row.getTransactionId(), row.getOperationId(), row.getSourceTransactionId(), row.getMerchantId(),
                row.getMerchantOrderNo(), row.getTransactionType(), row.getTransactionStatus(),
                row.getLabelCurrency(), row.getLabelAmount(), row.getApprovedCurrency(), row.getApprovedAmount(),
                row.getTransactionCurrency(), row.getTransactionAmount(), row.getCurrencyExponent(),
                row.getTransactionDateTime(), row.getTransactionUtcTime(), row.getTransactionTimeZone(), row.getVersion());
    }

    private ClearingCommandResponse response(ClearingTransactionFinanceStateDO state,
                                             String action,
                                             String status,
                                             String result) {
        ClearingCommandResponse response = new ClearingCommandResponse();
        response.setTransactionId(state.getTransactionId());
        response.setTransactionDateTime(state.getTransactionDateTime());
        response.setAction(action);
        response.setClearingStatus(status);
        response.setClearingRevision(value(state.getClearingRevision()));
        response.setVersion(state.getVersion() + 1);
        response.setResult(result);
        return response;
    }

    private String auditReason(String operator, String reason) {
        String normalizedOperator = requiredText(operator, "operator", OPERATOR_MAX_LENGTH);
        String normalizedReason = requiredText(reason, "reason", REASON_MAX_LENGTH - OPERATOR_MAX_LENGTH - 2);
        return normalizedOperator + ": " + normalizedReason;
    }

    private void validateRetry(String transactionId, ClearingRetryRequest request) {
        if (!StringUtils.hasText(transactionId) || request == null || request.getTransactionDateTime() == null
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw new IllegalArgumentException("manual retry identity, shard time and expected version are required");
        }
        requiredText(request.getReason(), "reason", REASON_MAX_LENGTH);
        requiredText(request.getOperator(), "operator", OPERATOR_MAX_LENGTH);
    }

    private void validateReview(String transactionId, ClearingReviewRequest request) {
        if (!StringUtils.hasText(transactionId) || request == null || request.getTransactionDateTime() == null
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw new IllegalArgumentException("manual review identity, shard time and expected version are required");
        }
        requiredText(request.getReason(), "reason", REASON_MAX_LENGTH);
        requiredText(request.getOperator(), "operator", OPERATOR_MAX_LENGTH);
    }

    private String requiredText(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim().replace('\r', ' ').replace('\n', ' ');
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private void requireOne(int affected, String action) {
        if (affected != 1) {
            throw new IllegalStateException(action + " did not affect one row");
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
