package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.api.HostedWalletSimulator.State;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.mapper.TransactionChannelInteractionLogMapper;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 开发钱包复用交易事实、终态事件和商户通知，调用方持有收银台会话行锁及交易数据源事务。 */
@Service
@Profile("dev & !test & !uat & !prod & !live-regression")
class CheckoutWalletTransactionLedger {
    private static final String PAYMENT = PaymentTransactionTypeEnum.PAYMENT.getCode();
    private static final String ORDER_FLOW_SCOPE = "MERCHANT_ORDER_FLOW";
    private static final String TIME_ZONE = "Asia/Shanghai";

    private final GlobalIdGenerator ids;
    private final TransactionRecordService records;
    private final TransactionIdempotencyService idempotency;
    private final TransactionChannelInteractionLogMapper interactionLogs;
    private final TransactionLifecycleEventService lifecycleEvents;

    CheckoutWalletTransactionLedger(GlobalIdGenerator ids, TransactionRecordService records,
                                    TransactionIdempotencyService idempotency,
                                    TransactionChannelInteractionLogMapper interactionLogs,
                                    TransactionLifecycleEventService lifecycleEvents) {
        this.ids = ids;
        this.records = records;
        this.idempotency = idempotency;
        this.interactionLogs = interactionLogs;
        this.lifecycleEvents = lifecycleEvents;
    }

    Identity open(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt, PaymentRouteResultDTO route,
                  Quote quote, boolean linked) {
        var identity = new Identity(ids.nextId(), "OP" + ids.nextId(),
                PaymentOrderNoGenerator.nextOrderNo("CRQ", attempt.getTransactionDateTime()));
        var command = command(session, attempt, identity, quote, linked);
        var result = result(command, identity, PaymentTransactionStatusEnum.PROCESSING,
                PaymentProcessStageEnum.WAITING_CALLBACK, null, null);
        String flowKey = idempotency.buildMerchantOrderFlowKey(command.getMerchantId(), command.getMerchantOrderNo());
        reserveMerchantOrderFlow(command, attempt.getCheckoutAttemptId(), flowKey);
        records.recordInitialTransaction(command, auditRoute(route), evidence(command, attempt, identity, null),
                result, null, quote.channelExponent());
        idempotency.complete(ORDER_FLOW_SCOPE, flowKey, identity.operationId(), identity.transactionId(),
                result.getStatus(), command.getTransactionAmount(), command.getTransactionCurrency(),
                JsonUtils.toJsonString(result));
        return identity;
    }

    private void reserveMerchantOrderFlow(PaymentCreateCommandDTO command, String attemptId, String flowKey) {
        // 与银行卡等首次交易共用商户订单流，避免两个收银台会话分别成功。
        var flow = idempotency.newProcessingRecord(ORDER_FLOW_SCOPE, flowKey, command.getMerchantId(),
                command.getMerchantOrderNo(), command.getMerchantOrderId(), PAYMENT, command.getTransactionDateTime(),
                TIME_ZONE, attemptId, LocalDateTime.now());
        if (idempotency.tryBegin(flow)) {
            return;
        }
        var existing = idempotency.find(ORDER_FLOW_SCOPE, flowKey).orElseThrow(
                () -> new ServiceException(ApiResultEnum.NETWORK_BUSY));
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(existing.getTransactionStatus())
                || !idempotency.tryRestartFailedFlow(existing, flow)) {
            throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS);
        }
    }

    void complete(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt, PaymentRouteResultDTO route,
                  State terminal, String failureCode, String failureMessage, boolean linked) {
        var identity = new Identity(attempt.getTransactionId(), attempt.getOperationId(), attempt.getChannelRequestId());
        Quote quote = JsonUtils.parseObject(attempt.getResultSnapshot(), Quote.class);
        var command = command(session, attempt, identity, quote, linked);
        var status = terminal == State.SUCCESS ? PaymentTransactionStatusEnum.SUCCESS : PaymentTransactionStatusEnum.FAILED;
        var result = result(command, identity, status, PaymentProcessStageEnum.FINISHED, failureCode, failureMessage);
        // 旧版待授权占位与本次终态在同一个事务中替换，失败时原占位随事务一起恢复。
        int reset = interactionLogs.resetWalletPreparationPlaceholder(identity.requestId(), attempt.getTransactionDateTime(),
                identity.transactionId(), attempt.getChannelOrderNo(), attempt.getChannelTransactionId());
        if (reset > 1) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY);
        }
        if (!records.completeInitialChannelResultAndReport(command, auditRoute(route),
                evidence(command, attempt, identity, terminal), result, null, quote.channelExponent())) {
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY);
        }
        lifecycleEvents.saveStatusChanged(identity.transactionId(), identity.operationId(), command.getMerchantId(),
                command.getMerchantOrderNo(), PAYMENT, status.getCode(), command.getTransactionDateTime());
    }

    private PaymentCreateCommandDTO command(PaymentCheckoutSessionDO session, PaymentCheckoutAttemptDO attempt,
                                             Identity identity, Quote quote, boolean linked) {
        var command = new PaymentCreateCommandDTO();
        command.setTransactionId(identity.transactionId());
        command.setMerchantId(attempt.getMerchantId());
        command.setMerchantOrderNo(attempt.getMerchantOrderNo());
        command.setMerchantOrderId(session.getMerchantRequestId());
        command.setRequestSource("HOSTED_CHECKOUT");
        command.setPaymentMethod(attempt.getPaymentMethod());
        command.setTransactionType(PAYMENT);
        command.setTransactionDateTime(attempt.getTransactionDateTime());
        command.setCallbackUrl(session.getMerchantNotifyUrl());
        if (linked && StringUtils.hasText(session.getBillingInfoJson())) {
            command.setBillingCardHolderInfo(JsonUtils.parseObject(session.getBillingInfoJson(),
                    PaymentCreateCommandDTO.BillingCardHolderInfoDTO.class));
        }
        command.setAmount(attempt.getLabelAmount());
        command.setCurrency(attempt.getLabelCurrency());
        command.setLabelAmount(attempt.getLabelAmount());
        command.setLabelCurrency(attempt.getLabelCurrency());
        command.setLabelCurrencyExponent(quote.labelExponent());
        command.setTransactionAmount(attempt.getChannelRequestAmount());
        command.setTransactionCurrency(attempt.getChannelRequestCurrency());
        command.setEdcEnabled(attempt.getLabelCurrency().equals(attempt.getChannelRequestCurrency()) ? 0 : 1);
        command.setTransactionRate(quote.rate());
        command.setRateSource(quote.rateSource());
        command.setRateTime(quote.rateTime());
        return command;
    }

    private PaymentCreateResultDTO result(PaymentCreateCommandDTO command, Identity identity,
                                          PaymentTransactionStatusEnum status, PaymentProcessStageEnum stage,
                                          String failureCode, String failureMessage) {
        var result = new PaymentCreateResultDTO();
        result.setTransactionId(identity.transactionId());
        result.setOperationId(identity.operationId());
        result.setMerchantId(command.getMerchantId());
        result.setMerchantOrderNo(command.getMerchantOrderNo());
        result.setMerchantOrderId(command.getMerchantOrderId());
        result.setPaymentMethod(command.getPaymentMethod());
        result.setTransactionType(PAYMENT);
        result.setTransactionDateTime(command.getTransactionDateTime());
        result.setTransactionTimeZone(TIME_ZONE);
        result.setStatus(status.getCode());
        result.setProcessStage(stage.getCode());
        result.setFailReasonCode(failureCode);
        result.setFailReasonMessage(failureMessage);
        result.setLabelAmount(command.getLabelAmount());
        result.setLabelCurrency(command.getLabelCurrency());
        result.setAmount(command.getLabelAmount().movePointRight(command.getLabelCurrencyExponent()).longValueExact());
        result.setCurrency(command.getLabelCurrency());
        result.setTransactionAmount(command.getTransactionAmount());
        result.setTransactionCurrency(command.getTransactionCurrency());
        return result;
    }

    private PaymentChannelInvokeResultDTO evidence(PaymentCreateCommandDTO command, PaymentCheckoutAttemptDO attempt,
                                                    Identity identity, State terminal) {
        var request = new ChannelPaymentRequest();
        request.setTransactionId(identity.transactionId());
        request.setOperationId(identity.operationId());
        request.setChannelCode(attempt.getChannelCode());
        request.setChannelOrderNo(attempt.getChannelOrderNo());
        request.setChannelTransactionId(attempt.getChannelTransactionId());
        request.setMerchantId(command.getMerchantId());
        request.setMerchantOrderNo(command.getMerchantOrderNo());
        request.setMerchantOrderId(command.getMerchantOrderId());
        request.setPaymentMethod(command.getPaymentMethod());
        request.setTransactionType(PAYMENT);
        request.setTransactionDateTime(command.getTransactionDateTime());
        request.setAmount(command.getTransactionAmount());
        request.setCurrency(command.getTransactionCurrency());
        var evidence = new PaymentChannelInvokeResultDTO();
        evidence.setChannelRequest(request);
        if (terminal != null) {
            var response = new ChannelPaymentResponse();
            response.setChannelCode(request.getChannelCode());
            response.setChannelOrderNo(request.getChannelOrderNo());
            response.setChannelTransactionId(request.getChannelTransactionId());
            response.setRawChannelStatus(switch (terminal) {
                case SUCCESS -> "SUCCESS";
                case CANCELLED -> "CANCELLED";
                case FAILED, EXPIRED -> "FAIL";
                default -> throw new IllegalArgumentException("wallet result must be terminal");
            });
            response.setChannelAmount(request.getAmount());
            response.setChannelCurrency(request.getCurrency());
            evidence.setChannelResponse(response);
        }
        evidence.setRequestId(identity.requestId());
        evidence.setRequestScene(PAYMENT);
        evidence.setHttpMethod("POST");
        evidence.setRequestStatus(terminal == null ? "SENT" : "SUCCESS");
        return evidence;
    }

    private PaymentRouteResultDTO auditRoute(PaymentRouteResultDTO source) {
        // 交易流水只保存渠道身份，禁止把临时凭据或钱包授权地址写入路由快照。
        var audit = PaymentRouteResultDTO.routed(source.getChannelCode());
        audit.setChannelId(source.getChannelId());
        audit.setMidConfigId(source.getMidConfigId());
        audit.setMidNo(source.getMidNo());
        return audit;
    }

    record Quote(int labelExponent, int channelExponent, BigDecimal rate, String rateSource, LocalDateTime rateTime,
                 WalletEndpoints walletEndpoints) {
    }

    record WalletEndpoints(String channelBaseUrl, String browserReturnBaseUrl, String callbackBaseUrl) {
    }

    record Identity(String transactionId, String operationId, String requestId) {
    }
}
