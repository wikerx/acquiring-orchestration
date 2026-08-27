package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecalculateRequest;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionResult;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionMerchantSnapshotDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionMerchantSnapshotMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingCompletionService;
import com.scott.payment.clearing.service.ClearingPreparationService;
import com.scott.payment.clearing.service.ClearingRecalculationService;
import com.scott.payment.clearing.service.FeeConfigurationSnapshotService;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 未结算单笔清分重算编排。目标费用版本在事务外加载，最终由完成服务以一个短事务切换修订和候选。
 */
@Service
public class DefaultClearingRecalculationService implements ClearingRecalculationService {

    private static final int REASON_MAX_LENGTH = 400;
    private static final int OPERATOR_MAX_LENGTH = 64;

    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    private final ClearingTransactionOperationMapper operationMapper;
    private final ClearingTransactionMerchantSnapshotMapper merchantSnapshotMapper;
    private final FeeConfigurationSnapshotService snapshotService;
    private final ClearingPreparationService preparationService;
    private final ClearingCompletionService completionService;
    private final Clock clock;

    @Autowired
    public DefaultClearingRecalculationService(ClearingTransactionFinanceStateMapper financeStateMapper,
                                               ClearingTransactionOperationMapper operationMapper,
                                               ClearingTransactionMerchantSnapshotMapper merchantSnapshotMapper,
                                               FeeConfigurationSnapshotService snapshotService,
                                               ClearingPreparationService preparationService,
                                               ClearingCompletionService completionService) {
        this(financeStateMapper, operationMapper, merchantSnapshotMapper, snapshotService,
                preparationService, completionService, Clock.systemUTC());
    }

    DefaultClearingRecalculationService(ClearingTransactionFinanceStateMapper financeStateMapper,
                                        ClearingTransactionOperationMapper operationMapper,
                                        ClearingTransactionMerchantSnapshotMapper merchantSnapshotMapper,
                                        FeeConfigurationSnapshotService snapshotService,
                                        ClearingPreparationService preparationService,
                                        ClearingCompletionService completionService,
                                        Clock clock) {
        this.financeStateMapper = financeStateMapper;
        this.operationMapper = operationMapper;
        this.merchantSnapshotMapper = merchantSnapshotMapper;
        this.snapshotService = snapshotService;
        this.preparationService = preparationService;
        this.completionService = completionService;
        this.clock = clock;
    }

    @Override
    public ClearingCommandResponse recalculate(String transactionId, ClearingRecalculateRequest request) {
        validateRequest(transactionId, request);
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectByTransaction(
                transactionId, request.getTransactionDateTime());
        ClearingTransactionOperationDO operation = operationMapper.selectByTransaction(
                transactionId, request.getTransactionDateTime());
        ClearingTransactionMerchantSnapshotDO originalSnapshot = merchantSnapshotMapper.selectByTransaction(
                transactionId, request.getTransactionDateTime());
        validateCurrentFacts(transactionId, request, state, operation, originalSnapshot);

        FeeVersionSnapshot targetSnapshot = snapshotService.loadForRecalculation(
                state.getMerchantId(), request.getTargetFeePlanId(), request.getTargetFeePlanVersionId(),
                originalSnapshot.getFeeSnapshotTime());
        ClearingOperationFacts facts = toFacts(operation);
        ClearingClaimResult syntheticClaim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.ACQUIRED, state.getFinanceStateId(),
                state.getClearingRevision(), state.getVersion(), facts);
        PaymentTransactionEventMessage message = message(operation, state, request);
        String processingOwner = "manual-recalc:" + request.getOperator().trim();
        CompletionCommand command = preparationService.prepareForRecalculation(
                message, syntheticClaim, processingOwner, targetSnapshot);
        CompletionResult result = completionService.recalculate(
                command, request.getExpectedVersion(), request.getExpectedClearingRevision(),
                LocalDateTime.now(clock));

        ClearingCommandResponse response = new ClearingCommandResponse();
        response.setTransactionId(transactionId);
        response.setTransactionDateTime(request.getTransactionDateTime());
        response.setAction("RECALCULATE");
        response.setClearingStatus(result.clearingStatus());
        response.setClearingRevision(result.clearingRevision());
        response.setVersion(request.getExpectedVersion() + 1);
        response.setResult("COMPLETED");
        return response;
    }

    private PaymentTransactionEventMessage message(ClearingTransactionOperationDO operation,
                                                    ClearingTransactionFinanceStateDO state,
                                                    ClearingRecalculateRequest request) {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("RECALC:" + state.getFinanceStateId() + ":"
                + request.getExpectedClearingRevision() + ":" + request.getTargetFeePlanVersionId());
        message.setCreatedAt(LocalDateTime.now(clock));
        message.setRetryCount(0);
        message.setTransactionId(operation.getTransactionId());
        message.setOperationId(operation.getOperationId());
        message.setMerchantId(operation.getMerchantId());
        message.setMerchantOrderNo(operation.getMerchantOrderNo());
        message.setTransactionType(operation.getTransactionType());
        message.setTransactionStatus(operation.getTransactionStatus());
        message.setEventType(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionDateTime(operation.getTransactionDateTime());
        return message;
    }

    private void validateCurrentFacts(String transactionId,
                                      ClearingRecalculateRequest request,
                                      ClearingTransactionFinanceStateDO state,
                                      ClearingTransactionOperationDO operation,
                                      ClearingTransactionMerchantSnapshotDO snapshot) {
        if (state == null || operation == null || snapshot == null
                || !Objects.equals(state.getTransactionId(), transactionId)
                || !Objects.equals(state.getOperationId(), operation.getOperationId())
                || !Objects.equals(state.getMerchantId(), operation.getMerchantId())
                || !Objects.equals(state.getTransactionDateTime(), operation.getTransactionDateTime())
                || !Objects.equals(snapshot.getTransactionId(), transactionId)
                || !Objects.equals(snapshot.getOperationId(), operation.getOperationId())
                || !Objects.equals(snapshot.getMerchantId(), operation.getMerchantId())
                || snapshot.getFeeSnapshotTime() == null
                || !Set.of(ClearingStateEnum.CLEARED.name(), ClearingStateEnum.NOT_REQUIRED.name())
                    .contains(state.getClearingStatus())
                || !"NOT_SETTLED".equals(state.getSettlementStatus())
                || !Objects.equals(state.getVersion(), request.getExpectedVersion())
                || !Objects.equals(state.getClearingRevision(), request.getExpectedClearingRevision())) {
            throw new IllegalStateException("clearing recalculation facts are missing, stale or already settled");
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

    private void validateRequest(String transactionId, ClearingRecalculateRequest request) {
        if (!StringUtils.hasText(transactionId) || request == null || request.getTransactionDateTime() == null
                || request.getExpectedVersion() == null || request.getExpectedVersion() < 0
                || request.getExpectedClearingRevision() == null || request.getExpectedClearingRevision() < 1
                || request.getTargetFeePlanId() == null || request.getTargetFeePlanId() < 1
                || request.getTargetFeePlanVersionId() == null || request.getTargetFeePlanVersionId() < 1) {
            throw new IllegalArgumentException("recalculation identity, expected state and target fee version are required");
        }
        requiredText(request.getReason(), "reason", REASON_MAX_LENGTH);
        requiredText(request.getOperator(), "operator", OPERATOR_MAX_LENGTH);
    }

    private void requiredText(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is missing or too long");
        }
    }
}
