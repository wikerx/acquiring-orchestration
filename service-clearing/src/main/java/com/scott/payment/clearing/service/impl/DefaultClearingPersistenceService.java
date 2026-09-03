package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingProjectionStatusEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionIdempotencyMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingPersistenceService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingPersistenceService
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 清分阶段A默认实现，以主库动作终态和 finance state CAS 为权威，不在事务内访问 Redis、Slave 或 MQ。
 * @status : create
 */
@Service
public class DefaultClearingPersistenceService implements ClearingPersistenceService {

    /**
     * {@code IDEMPOTENCY_KEY_PREFIX}常量，统一 {@code DefaultClearingPersistenceService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String IDEMPOTENCY_KEY_PREFIX = "service-clearing-transaction-status:";
    private static final Set<String> TERMINAL_TRANSACTION_STATUSES = Set.of("SUCCESS", "FAILED");

    private final ClearingTransactionOperationMapper operationMapper;
    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    private final ClearingTransactionIdempotencyMapper idempotencyMapper;
    private final GlobalIdGenerator globalIdGenerator;
    private final ClearingProperties properties;

    /**
     * 创建阶段A持久化服务。
     *
     * @param operationMapper 动作事实 Mapper
     * @param financeStateMapper 清分状态 CAS Mapper
     * @param idempotencyMapper MQ 成功消费幂等 Mapper
     * @param globalIdGenerator finance state 业务号生成器
     * @param properties 清分租约参数
     */
    public DefaultClearingPersistenceService(ClearingTransactionOperationMapper operationMapper,
                                             ClearingTransactionFinanceStateMapper financeStateMapper,
                                             ClearingTransactionIdempotencyMapper idempotencyMapper,
                                             GlobalIdGenerator globalIdGenerator,
                                             ClearingProperties properties) {
        this.operationMapper = operationMapper;
        this.financeStateMapper = financeStateMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.globalIdGenerator = globalIdGenerator;
        this.properties = properties;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ClearingClaimResult claim(PaymentTransactionEventMessage message,
                                     String processingOwner,
                                     LocalDateTime now) {
        requireClaimArguments(message, processingOwner, now);
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + message.getMessageId();
        if (idempotencyMapper.existsSuccessfulConsumption(idempotencyKey)) {
            return new ClearingClaimResult(ClearingClaimResult.Outcome.ALREADY_CONSUMED,
                    null, 0, 0, null);
        }

        ClearingTransactionOperationDO operation = operationMapper.selectByTransaction(
                message.getTransactionId(), message.getTransactionDateTime());
        validateAuthoritativeOperation(message, operation);
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectByTransaction(
                message.getTransactionId(), message.getTransactionDateTime());
        if (state == null) {
            financeStateMapper.insertIfAbsent("FS" + globalIdGenerator.nextId(), operation, now);
            state = financeStateMapper.selectByTransaction(
                    message.getTransactionId(), message.getTransactionDateTime());
        }
        validateFinanceState(operation, state);
        ClearingOperationFacts facts = toFacts(operation, false);
        ClearingStateEnum currentState = ClearingStateEnum.valueOf(state.getClearingStatus());
        if (currentState.isCompletedTerminal()) {
            return result(ClearingClaimResult.Outcome.ALREADY_COMPLETED, state, facts, false);
        }
        if (currentState == ClearingStateEnum.MANUAL_REVIEW) {
            return result(ClearingClaimResult.Outcome.MANUAL_REVIEW_REQUIRED, state, facts, false);
        }
        if (!(message instanceof ClearingRetryDueMessage)
                && (currentState == ClearingStateEnum.WAITING_SOURCE || currentState == ClearingStateEnum.FAILED)) {
            validateScheduledRetryState(state);
            return result(ClearingClaimResult.Outcome.RETRY_ALREADY_SCHEDULED, state, facts, false);
        }
        RetryClaimDecision retryDecision = retryClaimDecision(message, state, now);
        if (retryDecision == RetryClaimDecision.STALE) {
            return result(ClearingClaimResult.Outcome.STALE_RETRY, state, facts, false);
        }
        if (retryDecision == RetryClaimDecision.NOT_DUE) {
            return result(ClearingClaimResult.Outcome.BUSY, state, facts, false);
        }

        LocalDateTime deadline = now.plusSeconds(properties.getProcessingTimeoutSeconds());
        int claimed = financeStateMapper.claimProcessing(
                operation.getTransactionId(), operation.getTransactionDateTime(), state.getVersion(),
                processingOwner, now, deadline, message.getMessageId());
        if (claimed != 1) {
            return result(ClearingClaimResult.Outcome.BUSY, state, facts, false);
        }
        int projected = operationMapper.updateClearingProjection(
                operation.getTransactionId(), operation.getTransactionDateTime(), requiredVersion(operation.getVersion()),
                ClearingProjectionStatusEnum.PENDING.name(), null, null, now);
        if (projected != 1) {
            throw new IllegalStateException("clearing claim operation projection CAS did not update one row");
        }
        return result(ClearingClaimResult.Outcome.ACQUIRED, state, toFacts(operation, true), true);
    }

    /** 延时消息只有在失败码、修订和重试序号仍匹配时才允许重新领取。 */
    private RetryClaimDecision retryClaimDecision(PaymentTransactionEventMessage message,
                                                  ClearingTransactionFinanceStateDO state,
                                                  LocalDateTime now) {
        if (!(message instanceof ClearingRetryDueMessage retryMessage)) {
            return RetryClaimDecision.NOT_RETRY;
        }
        boolean controlsMatch = (ClearingStateEnum.WAITING_SOURCE.name().equals(state.getClearingStatus())
                || ClearingStateEnum.FAILED.name().equals(state.getClearingStatus()))
                && Objects.equals(state.getClearingRevision(), retryMessage.getExpectedClearingRevision())
                && Objects.equals(state.getClearingRetryCount(), retryMessage.getClearingRetryCount())
                && Objects.equals(state.getLastFailureCode(), retryMessage.getRetryReasonCode())
                && state.getNextRetryTime() != null
                && retryMessage.getDeliverAt() != null
                && Objects.equals(state.getNextRetryTime().toInstant(ZoneOffset.UTC), retryMessage.getDeliverAt());
        if (!controlsMatch) {
            return RetryClaimDecision.STALE;
        }
        return state.getNextRetryTime().isAfter(now)
                || retryMessage.getDeliverAt().isAfter(now.toInstant(ZoneOffset.UTC))
                ? RetryClaimDecision.NOT_DUE : RetryClaimDecision.DUE;
    }

    /** 被排期重试的状态必须仍可领取，人工复核和完成态不得覆盖。 */
    private void validateScheduledRetryState(ClearingTransactionFinanceStateDO state) {
        if (state.getNextRetryTime() == null || state.getClearingRetryCount() == null
                || state.getClearingRetryCount() < 1 || !StringUtils.hasText(state.getLastFailureCode())) {
            throw new IllegalStateException("retryable clearing state is missing scheduled retry controls");
        }
    }

    private ClearingClaimResult result(ClearingClaimResult.Outcome outcome,
                                       ClearingTransactionFinanceStateDO state,
                                       ClearingOperationFacts facts,
                                       boolean versionAdvanced) {
        return new ClearingClaimResult(outcome, state.getFinanceStateId(),
                value(state.getClearingRevision()), value(state.getVersion()) + (versionAdvanced ? 1 : 0), facts);
    }

    /** Stage A 领取前校验消息身份、分片时间和租约 owner。 */
    private void requireClaimArguments(PaymentTransactionEventMessage message,
                                       String processingOwner,
                                       LocalDateTime now) {
        if (message == null || !StringUtils.hasText(message.getMessageId())
                || !StringUtils.hasText(message.getTransactionId())
                || message.getTransactionDateTime() == null) {
            throw new IllegalArgumentException("clearing message identity and transactionDateTime are required");
        }
        if (!StringUtils.hasText(processingOwner) || now == null) {
            throw new IllegalArgumentException("processing owner and claim time are required");
        }
    }

    /** 消息只作触发，交易终态、金额和身份全部以主库动作事实为准。 */
    private void validateAuthoritativeOperation(PaymentTransactionEventMessage message,
                                                ClearingTransactionOperationDO operation) {
        if (operation == null) {
            throw new IllegalStateException("authoritative transaction operation is missing");
        }
        boolean identityMatches = Objects.equals(message.getTransactionId(), operation.getTransactionId())
                && Objects.equals(message.getOperationId(), operation.getOperationId())
                && Objects.equals(message.getMerchantId(), operation.getMerchantId())
                && Objects.equals(message.getTransactionDateTime(), operation.getTransactionDateTime())
                && (!StringUtils.hasText(message.getTransactionType())
                    || Objects.equals(message.getTransactionType(), operation.getTransactionType()));
        if (!identityMatches) {
            throw new IllegalStateException("clearing message identity does not match authoritative operation");
        }
        if (!TERMINAL_TRANSACTION_STATUSES.contains(operation.getTransactionStatus())) {
            throw new IllegalStateException("transaction operation is not in an authoritative terminal status");
        }
    }

    /** 新建或锁定后的 finance state 必须与权威动作身份一致。 */
    private void validateFinanceState(ClearingTransactionOperationDO operation,
                                      ClearingTransactionFinanceStateDO state) {
        if (state == null) {
            throw new IllegalStateException("transaction finance state is missing");
        }
        if (!StringUtils.hasText(state.getFinanceStateId())
                || !Objects.equals(operation.getTransactionId(), state.getTransactionId())
                || !Objects.equals(operation.getOperationId(), state.getOperationId())
                || !Objects.equals(operation.getMerchantId(), state.getMerchantId())
                || !Objects.equals(operation.getSourceTransactionId(), state.getSourceTransactionId())
                || !Objects.equals(operation.getLabelCurrency(), state.getLabelCurrency())
                || !Objects.equals(operation.getTransactionType(), state.getTransactionType())
                || !Objects.equals(operation.getTransactionDateTime(), state.getTransactionDateTime())
                || !Objects.equals(operation.getTransactionUtcTime(), state.getTransactionUtcTime())
                || !Objects.equals(operation.getTransactionTimeZone(), state.getTransactionTimeZone())
                || !StringUtils.hasText(state.getClearingStatus())
                || state.getClearingRevision() == null || state.getClearingRevision() < 0
                || state.getVersion() == null || state.getVersion() < 0) {
            throw new IllegalStateException("transaction finance state identity is invalid");
        }
        try {
            ClearingStateEnum.valueOf(state.getClearingStatus());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("transaction finance state contains an unsupported status", exception);
        }
    }

    private ClearingOperationFacts toFacts(ClearingTransactionOperationDO operation, boolean versionAdvanced) {
        return new ClearingOperationFacts(
                operation.getTransactionId(), operation.getOperationId(), operation.getSourceTransactionId(),
                operation.getMerchantId(), operation.getMerchantOrderNo(), operation.getTransactionType(),
                operation.getTransactionStatus(), operation.getLabelCurrency(), operation.getLabelAmount(),
                operation.getApprovedCurrency(), operation.getApprovedAmount(), operation.getTransactionCurrency(),
                operation.getTransactionAmount(), operation.getCurrencyExponent(), operation.getTransactionDateTime(),
                operation.getTransactionUtcTime(), operation.getTransactionTimeZone(),
                requiredVersion(operation.getVersion()) + (versionAdvanced ? 1 : 0));
    }

    private int requiredVersion(Integer version) {
        if (version == null || version < 0) {
            throw new IllegalStateException("transaction operation version is invalid");
        }
        return version;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private enum RetryClaimDecision {
        /**
         * NOT RETRY 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        NOT_RETRY,
        /**
         * DUE 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        DUE,
        /**
         * NOT DUE 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        NOT_DUE,
        STALE
    }
}
