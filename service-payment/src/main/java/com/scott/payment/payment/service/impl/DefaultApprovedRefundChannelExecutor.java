package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.ApprovedRefundChannelExecutor;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.RefundChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultApprovedRefundChannelExecutor
 * @date : 2026-08-06 00:00
 * @description : 已批准退款渠道执行实现，从持久化快照恢复固定渠道身份，并复用现有退款结果 CAS、金额累计和终态通知链路。
 * @status : create
 */
@Service
public class DefaultApprovedRefundChannelExecutor implements ApprovedRefundChannelExecutor {

    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    private final TransactionRecordService transactionRecordService;
    private final PaymentChannelRouteService channelRouteService;
    private final PaymentChannelInvokeService channelInvokeService;
    private final ChannelTransactionStatusResolver channelStatusResolver;
    private final RefundChannelResultTransactionService channelResultTransactionService;
    private final TransactionIdempotencyService idempotencyService;

    /**
     * 创建已批准退款渠道执行器。
     */
    public DefaultApprovedRefundChannelExecutor(TransactionRecordService transactionRecordService,
                                                PaymentChannelRouteService channelRouteService,
                                                PaymentChannelInvokeService channelInvokeService,
                                                ChannelTransactionStatusResolver channelStatusResolver,
                                                RefundChannelResultTransactionService channelResultTransactionService,
                                                TransactionIdempotencyService idempotencyService) {
        this.transactionRecordService = transactionRecordService;
        this.channelRouteService = channelRouteService;
        this.channelInvokeService = channelInvokeService;
        this.channelStatusResolver = channelStatusResolver;
        this.channelResultTransactionService = channelResultTransactionService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * 使用审批受理时持久化的渠道请求身份执行退款。
     *
     * <p>该方法不重新路由，也不生成第二个渠道交易号。渠道异常结果按 outcomeUncertain 保持非终态，
     * 由后续主动 QUERY 或回调确认，避免把未知结果误判失败后重复退款。</p>
     */
    @Override
    public void execute(TransactionOperationDO operationDO,
                        TransactionChannelRequestDO requestDO,
                        RefundExecutionMessage message) {
        validate(operationDO, requestDO, message);
        TransactionOrderDO sourceOrder = transactionRecordService.findSourceOrderByTransactionId(
                message.getSourceTransactionId(),
                message.getSourceTransactionDateTime(),
                message.getRootTransactionDateTime());
        if (sourceOrder == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        TransactionOperationDO sourceOperation = transactionRecordService.findSourceOperationByTransactionId(
                message.getSourceTransactionId(), message.getSourceTransactionDateTime());
        PaymentCreateCommandDTO command = buildCommand(operationDO, sourceOrder, sourceOperation, message);
        PaymentRouteResultDTO route = channelRouteService.restore(
                operationDO.getChannelCode(), operationDO.getChannelId(),
                operationDO.getChannelMidConfigId(), operationDO.getChannelTerminalId());
        PaymentPreparedChannelRequestDTO preparedRequest = buildPreparedRequest(requestDO);
        PaymentChannelInvokeResultDTO invokeResult = invokeSafely(
                command, route, operationDO, preparedRequest);
        PaymentCreateResultDTO result = buildResult(operationDO, sourceOrder, command, invokeResult);

        RefundPreparationResultDTO preparation = new RefundPreparationResultDTO();
        preparation.setCallChannel(false);
        preparation.setIdempotencyKey(buildIdempotencyKey(operationDO));
        preparation.setCommandDTO(command);
        preparation.setSourceOrderDO(sourceOrder);
        preparation.setRouteResultDTO(route);
        preparation.setPreparedChannelRequestDTO(preparedRequest);
        preparation.setResultDTO(result);
        preparation.setCurrencyExponent(operationDO.getCurrencyExponent() == null
                ? 0 : operationDO.getCurrencyExponent());
        channelResultTransactionService.recordRefundChannelResult(preparation, invokeResult);
        idempotencyService.complete(
                TRANSACTION_OPERATION_SCOPE,
                preparation.getIdempotencyKey(),
                operationDO.getOperationId(),
                operationDO.getTransactionId(),
                result.getStatus(),
                operationDO.getTransactionAmount(),
                operationDO.getTransactionCurrency(),
                JsonUtils.toJsonString(result));
    }

    private PaymentCreateCommandDTO buildCommand(TransactionOperationDO operationDO,
                                                 TransactionOrderDO sourceOrder,
                                                 TransactionOperationDO sourceOperation,
                                                 RefundExecutionMessage message) {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId(operationDO.getMerchantId());
        command.setMerchantOrderNo(operationDO.getMerchantOrderNo());
        command.setMerchantOrderId(operationDO.getMerchantOperationNo());
        command.setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
        command.setAmount(operationDO.getTransactionAmount());
        command.setCurrency(operationDO.getTransactionCurrency());
        command.setLabelAmount(operationDO.getLabelAmount());
        command.setLabelCurrency(operationDO.getLabelCurrency());
        command.setTransactionAmount(operationDO.getTransactionAmount());
        command.setTransactionCurrency(operationDO.getTransactionCurrency());
        command.setTransactionRate(operationDO.getTransactionRate());
        command.setPaymentMethod(sourceOrder.getPaymentMethod());
        command.setTransactionDateTime(operationDO.getTransactionDateTime());
        command.setRequestSource(operationDO.getRequestSource());
        command.setApplicantId(operationDO.getApplicantId());
        command.setApplicantName(operationDO.getApplicantName());
        command.setRequestReason(operationDO.getRequestReason());
        command.setRefundScope(operationDO.getRefundScope());
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfo.setSourceTransactionId(message.getSourceTransactionId());
        transactionInfo.setSourceTransactionDateTime(message.getSourceTransactionDateTime());
        transactionInfo.setRootTransactionDateTime(message.getRootTransactionDateTime());
        transactionInfo.setSourceChannelTransactionId(sourceOperation == null
                ? null : sourceOperation.getChannelTransactionId());
        command.setTransactionInfo(transactionInfo);
        return command;
    }

    private PaymentPreparedChannelRequestDTO buildPreparedRequest(TransactionChannelRequestDO requestDO) {
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId(requestDO.getRequestId());
        prepared.setChannelOrderNo(requestDO.getChannelOrderNo());
        prepared.setChannelTransactionId(requestDO.getChannelTransactionId());
        return prepared;
    }

    private PaymentChannelInvokeResultDTO invokeSafely(PaymentCreateCommandDTO command,
                                                       PaymentRouteResultDTO route,
                                                       TransactionOperationDO operation,
                                                       PaymentPreparedChannelRequestDTO prepared) {
        try {
            return channelInvokeService.invoke(
                    command, route, operation.getOperationId(), operation.getTransactionId(), prepared);
        } catch (DefaultPaymentChannelInvokeService.PaymentChannelInvokeException exception) {
            return exception.getInvokeResult();
        }
    }

    private PaymentCreateResultDTO buildResult(TransactionOperationDO operation,
                                               TransactionOrderDO sourceOrder,
                                               PaymentCreateCommandDTO command,
                                               PaymentChannelInvokeResultDTO invokeResult) {
        ChannelPaymentResponse response = invokeResult == null ? null : invokeResult.getChannelResponse();
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setTransactionId(operation.getTransactionId());
        result.setSourceTransactionId(operation.getSourceTransactionId());
        result.setOperationId(operation.getOperationId());
        result.setMerchantOrderNo(operation.getMerchantOrderNo());
        result.setMerchantOrderId(operation.getMerchantOperationNo());
        result.setMerchantId(operation.getMerchantId());
        result.setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
        result.setOrderAmount(operation.getLabelAmount());
        result.setOrderCurrency(operation.getLabelCurrency());
        result.setLabelAmount(operation.getLabelAmount());
        result.setLabelCurrency(operation.getLabelCurrency());
        result.setTransactionAmount(operation.getTransactionAmount());
        result.setTransactionCurrency(operation.getTransactionCurrency());
        result.setTransactionRate(operation.getTransactionRate());
        result.setCurrency(operation.getTransactionCurrency());
        result.setAmount(toMinorAmount(operation.getTransactionAmount(), operation.getCurrencyExponent()));
        result.setTransactionDateTime(operation.getTransactionDateTime());
        result.setRootTransactionDateTime(sourceOrder.getTransactionDateTime());
        result.setTransactionTimeZone(operation.getTransactionTimeZone());
        result.setPaymentMethod(sourceOrder.getPaymentMethod());
        result.setPaymentBrand(sourceOrder.getPaymentBrand());
        result.setTotalAuthorizedAmount(sourceOrder.getAuthorizedAmount());
        result.setTotalCapturedAmount(sourceOrder.getCapturedAmount());
        result.setTotalAuthorizedCancelAmount(sourceOrder.getAuthorizedCancelAmount());
        result.setTotalRefundAmount(sourceOrder.getRefundedAmount());
        result.setTotalRefuseAmount(sourceOrder.getChargebackAmount());
        applyChannelStatus(result, invokeResult, response);
        if (response != null) {
            result.setAuthCode(response.getAuthCode());
            result.setAcquirerReferenceNo(response.getAcquirerReferenceNo());
        }
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(result.getStatus())) {
            result.setTotalRefundAmount(zeroIfNull(sourceOrder.getRefundedAmount())
                    .add(zeroIfNull(operation.getTransactionAmount())));
        }
        return result;
    }

    private void applyChannelStatus(PaymentCreateResultDTO result,
                                    PaymentChannelInvokeResultDTO invokeResult,
                                    ChannelPaymentResponse response) {
        if (isInvokeFailed(invokeResult)) {
            if (invokeResult.isOutcomeUncertain()) {
                result.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
                result.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
                result.setPendingReasonCode("CHANNEL_RESULT_UNKNOWN");
            } else {
                result.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
                result.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
                result.setFailReasonCode(PaymentFailureReasonEnum.CHANNEL_REQUEST_FAILED.getCode());
                result.setFailReasonMessage("channel request failed");
            }
            return;
        }
        ChannelTransactionStatusResolution resolution = channelStatusResolver.resolveSync(
                response == null ? null : response.getChannelCode(),
                PaymentTransactionTypeEnum.REFUND.getCode(), response);
        result.setStatus(resolution.getTargetStatus());
        result.setProcessStage(resolution.getProcessStage());
        result.setPendingReasonCode(resolution.getPendingReasonCode());
        result.setFailReasonCode(resolution.getFailReasonCode());
        result.setFailReasonMessage(resolution.getFailReasonMessage());
    }

    private boolean isInvokeFailed(PaymentChannelInvokeResultDTO invokeResult) {
        return invokeResult != null
                && (StringUtils.hasText(invokeResult.getExceptionType())
                || "FAILED".equals(invokeResult.getRequestStatus())
                || "TIMEOUT".equals(invokeResult.getRequestStatus()));
    }

    private String buildIdempotencyKey(TransactionOperationDO operation) {
        String merchantOperationNo = operation.getSourceTransactionId() + ":" + operation.getMerchantOperationNo();
        return idempotencyService.buildTransactionOperationKey(
                operation.getMerchantId(), merchantOperationNo, PaymentTransactionTypeEnum.REFUND.getCode());
    }

    private Long toMinorAmount(BigDecimal amount, Integer exponent) {
        if (amount == null || exponent == null || exponent < 0) {
            return null;
        }
        return amount.movePointRight(exponent).longValueExact();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validate(TransactionOperationDO operationDO,
                          TransactionChannelRequestDO requestDO,
                          RefundExecutionMessage message) {
        if (operationDO == null || requestDO == null || message == null
                || !PaymentTransactionTypeEnum.REFUND.getCode().equals(operationDO.getTransactionType())
                || !PaymentTransactionStatusEnum.PROCESSING.getCode().equals(operationDO.getTransactionStatus())
                || !PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode().equals(operationDO.getProcessStage())
                || !"INIT".equals(requestDO.getRequestStatus())) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT);
        }
    }
}
