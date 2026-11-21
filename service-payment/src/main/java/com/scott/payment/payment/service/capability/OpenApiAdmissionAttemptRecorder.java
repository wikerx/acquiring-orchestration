package com.scott.payment.payment.service.capability;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.entity.TransactionMerchantApiInteractionLogDO;
import com.scott.payment.payment.mapper.TransactionIdempotencyMapper;
import com.scott.payment.payment.mapper.TransactionMerchantApiInteractionLogMapper;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** 在原操作幂等键上记录 OpenAPI 建单前的确定拒绝。 */
@Service
public class OpenApiAdmissionAttemptRecorder {

    private static final String OPERATION_SCOPE = "TRANSACTION_OPERATION";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ENTRY = "OPENAPI";
    private static final String STAGE = "ADMISSION";
    private static final String OUTCOME = "REJECTED";
    private static final String FAILED = PaymentTransactionStatusEnum.FAILED.getCode();

    private final TransactionIdempotencyService idempotencyService;
    private final TransactionIdempotencyMapper idempotencyMapper;
    private final TransactionMerchantApiInteractionLogMapper interactionMapper;

    public OpenApiAdmissionAttemptRecorder(TransactionIdempotencyService idempotencyService,
                                          TransactionIdempotencyMapper idempotencyMapper,
                                          TransactionMerchantApiInteractionLogMapper interactionMapper) {
        this.idempotencyService = idempotencyService;
        this.idempotencyMapper = idempotencyMapper;
        this.interactionMapper = interactionMapper;
    }

    /**
     * 两种事实共同提交。插入失败时，仅按原动作键读取唯一提交胜者。
     * 应由明确拒绝的准备流程调用，并持有原动作幂等锁。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public TransactionIdempotencyDO recordKnownRejection(PaymentCreateCommandDTO command,
                                                         String actionKey,
                                                         PaymentAdmissionRejectedException rejection) {
        if (!command.isTrustedOpenApiAdmissionRequest() || command.getOpenApiRequestTime() == null
                || command.getRequestFingerprint() == null) {
            throw new IllegalArgumentException("a trusted OpenAPI request and canonical action identity are required");
        }
        LocalDateTime occurredAt = command.getOpenApiRequestTime().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE).truncatedTo(ChronoUnit.MILLIS);
        String attemptId = attemptId(actionKey);
        String contextJson = JsonUtils.toJsonString(new RejectedAttempt(1, ENTRY, rejection.itemKey(),
                command.getTransactionType(), attemptId, STAGE, OUTCOME, rejection.reason(),
                rejection.authorizationRevision(), occurredAt));

        TransactionIdempotencyDO candidate = idempotencyService.newProcessingRecord(OPERATION_SCOPE, actionKey,
                command.getMerchantId(), command.getMerchantOrderNo(), command.getMerchantOrderId(),
                command.getTransactionType(), occurredAt, BUSINESS_ZONE.getId(), command.getRequestFingerprint(), now);
        candidate.setTransactionStatus(FAILED);
        candidate.setResultSnapshot(contextJson);
        candidate.setRequestAmount(command.getAmount());
        candidate.setRequestCurrency(command.getCurrency());
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            try {
                if (idempotencyMapper.insert(candidate) != 1) {
                    throw new IllegalStateException("could not record OpenAPI admission attempt identity");
                }
            } catch (DuplicateKeyException exception) {
                return idempotencyService.find(OPERATION_SCOPE, actionKey).orElseThrow(() -> exception);
            }
            TransactionMerchantApiInteractionLogDO log = toInteraction(command, rejection, candidate, attemptId, now);
            if (interactionMapper.insertAdmissionRejection(log) != 1) {
                throw new IllegalStateException("could not record OpenAPI admission attempt fact");
            }
            return candidate;
        }
    }

    /** 锁忙及提交状态未知的恢复必须读取原动作幂等事实所在的交易主库。 */
    @DS(DataSourceName.TRANSACTION)
    public Optional<TransactionIdempotencyDO> findCommittedAction(String actionKey) {
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            return idempotencyService.find(OPERATION_SCOPE, actionKey);
        }
    }

    private TransactionMerchantApiInteractionLogDO toInteraction(PaymentCreateCommandDTO command,
                                                                 PaymentAdmissionRejectedException rejection,
                                                                 TransactionIdempotencyDO identity, String attemptId,
                                                                 LocalDateTime now) {
        TransactionMerchantApiInteractionLogDO log = new TransactionMerchantApiInteractionLogDO();
        log.setApiLogId(attemptId);
        log.setRequestId(command.getRequestId());
        log.setMerchantId(identity.getMerchantId());
        log.setMerchantOrderNo(identity.getMerchantOrderNo());
        log.setMerchantOrderId(identity.getMerchantOrderId());
        log.setApiOperation(identity.getTransactionType());
        log.setRequestPath(command.getOpenApiRequestPath());
        log.setRequestTime(identity.getTransactionDateTime());
        log.setRequestResult(FAILED);
        log.setResponseTime(now);
        log.setResponseResult(FAILED);
        log.setMerchantResponseCode(rejection.getCode());
        log.setMerchantResponseMessage(rejection.getMessage());
        long durationMillis = ChronoUnit.MILLIS.between(identity.getTransactionDateTime(), now);
        log.setDurationMillis((int) Math.min(Math.max(0, durationMillis), Integer.MAX_VALUE));
        log.setTraceId(TraceContext.getTraceId());
        log.setTransactionDateTime(identity.getTransactionDateTime());
        log.setTransactionUtcTime(identity.getTransactionUtcTime());
        log.setTransactionTimeZone(identity.getTransactionTimeZone());
        log.setCreateTime(now);
        log.setAdmissionContextJson(identity.getResultSnapshot());
        return log;
    }

    /** 命中无系统订单的拒绝凭证时，按同一规范指纹返回原对外拒绝。 */
    public static void rethrowIfRejected(TransactionIdempotencyDO record, String canonicalFingerprint) {
        if (record.getTransactionId() != null || record.getOperationId() != null
                || !FAILED.equals(record.getTransactionStatus()) || record.getResultSnapshot() == null) {
            return;
        }
        JSONObject context = JsonUtils.parseObject(record.getResultSnapshot(), JSONObject.class);
        if (context == null || context.getIntValue("schemaVersion") != 1 || !ENTRY.equals(context.getString("entry"))
                || !STAGE.equals(context.getString("admissionStage")) || !OUTCOME.equals(context.getString("outcome"))) {
            return;
        }
        if (!Objects.equals(record.getRequestFingerprint(), canonicalFingerprint)) {
            throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT.getCode(),
                    "merchant order number already has a different payment request");
        }
        throw new PaymentAdmissionRejectedException(context.getString("itemKey"),
                PaymentAdmissionRejectedException.Reason.valueOf(context.getString("reasonCode")),
                context.getLong("authorizationRevision"), true);
    }

    private static String attemptId(String actionKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((OPERATION_SCOPE + ":" + actionKey).getBytes(StandardCharsets.UTF_8));
            return "MALR" + HexFormat.of().formatHex(digest, 0, 30);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record RejectedAttempt(int schemaVersion, String entry, String itemKey, String transactionType,
                                   String attemptKey, String admissionStage, String outcome,
                                   PaymentAdmissionRejectedException.Reason reasonCode, Long authorizationRevision,
                                   LocalDateTime occurredAt) {
    }
}
