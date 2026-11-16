package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentPendingReasonEnum;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 已批准退款的渠道执行器。
 *
 * <p>从审批受理时的持久化快照恢复渠道、MID 和请求身份，不重新路由或生成退款号。渠道结果继续复用
 * 标准退款 CAS、金额累计和终态通知链路；网络超时等未知结果保持非终态，等待查询或回调确认。</p>
 */
@Service
@Slf4j
public class DefaultApprovedRefundChannelExecutor implements ApprovedRefundChannelExecutor {

    /**
     * 退款动作幂等记录所在作用域，与准备阶段保持完全一致。
     */
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
        log.info("开始执行已审批退款渠道请求, event: APPROVED_REFUND_CHANNEL_EXECUTION_STARTED, transactionId: {}, operationId: {}, requestId: {}",
                operationDO.getTransactionId(), operationDO.getOperationId(), requestDO.getRequestId());
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
        // 审批期间路由配置可能变化，因此必须从已持久化交易快照恢复原渠道身份，禁止重新选路。
        PaymentRouteResultDTO route = OriginalTransactionRouteResolver.restore(
                channelRouteService, sourceOrder, operationDO);
        // requestId、channelOrderNo 和 channelTransactionId 在申请阶段已经冻结，审批后只允许原样复用。
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
        boolean statusChanged = channelResultTransactionService.recordRefundChannelResult(preparation, invokeResult);
        if (!statusChanged) {
            // CAS 失败通常表示回调或查询已先落终态，返回值必须回读数据库而不能覆盖先到结果。
            TransactionOperationDO persistedOperation = transactionRecordService.findSourceOperationByTransactionIdPrimary(
                    operationDO.getTransactionId(), operationDO.getTransactionDateTime());
            TransactionOrderDO persistedOrder = transactionRecordService.findOrderPrimary(
                    message.getRootTransactionDateTime(), operationDO.getOperationId());
            PaymentPersistedResultSupport.overwriteAuthoritativeFields(
                    result, persistedOperation, persistedOrder);
        }
        idempotencyService.complete(
                TRANSACTION_OPERATION_SCOPE,
                preparation.getIdempotencyKey(),
                operationDO.getOperationId(),
                operationDO.getTransactionId(),
                result.getStatus(),
                operationDO.getTransactionAmount(),
                operationDO.getTransactionCurrency(),
                JsonUtils.toJsonString(result));
        log.info("已审批退款渠道执行完成, event: APPROVED_REFUND_CHANNEL_EXECUTION_COMPLETED, transactionId: {}, status: {}, statusChanged: {}, outcomeUncertain: {}",
                operationDO.getTransactionId(), result.getStatus(), statusChanged,
                invokeResult != null && invokeResult.isOutcomeUncertain());
    }

    /**
     * 从已审批退款动作构建渠道退款命令。
     */
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

    /**
     * 构建已准备请求。
     */
    private PaymentPreparedChannelRequestDTO buildPreparedRequest(TransactionChannelRequestDO requestDO) {
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId(requestDO.getRequestId());
        prepared.setChannelOrderNo(requestDO.getChannelOrderNo());
        prepared.setChannelTransactionId(requestDO.getChannelTransactionId());
        return prepared;
    }

    /**
     * 调用渠道并把超时或未知结果保留为非终态。
     */
    private PaymentChannelInvokeResultDTO invokeSafely(PaymentCreateCommandDTO command,
                                                       PaymentRouteResultDTO route,
                                                       TransactionOperationDO operation,
                                                       PaymentPreparedChannelRequestDTO prepared) {
        try {
            return channelInvokeService.invoke(
                    command, route, operation.getOperationId(), operation.getTransactionId(), prepared);
        } catch (DefaultPaymentChannelInvokeService.PaymentChannelInvokeException exception) {
            // 渠道调用服务已结构化记录发送状态；此处返回结果交由统一状态解析，不能直接把异常等同为失败。
            log.warn("已审批退款渠道调用返回异常结果, event: APPROVED_REFUND_CHANNEL_INVOKE_EXCEPTION, transactionId: {}, requestId: {}, exceptionType: {}, outcomeUncertain: {}",
                    operation.getTransactionId(), prepared.getRequestId(),
                    exception.getClass().getSimpleName(),
                    exception.getInvokeResult() != null && exception.getInvokeResult().isOutcomeUncertain());
            return exception.getInvokeResult();
        }
    }

    /**
     * 构建已审批退款的渠道执行结果。
     */
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
        result.setCurrency(operation.getLabelCurrency());
        result.setAmount(toMinorAmount(operation.getLabelAmount(), operation.getLabelCurrencyExponent()));
        result.setTransactionDateTime(operation.getTransactionDateTime());
        result.setRootTransactionDateTime(sourceOrder.getTransactionDateTime());
        result.setTransactionTimeZone(operation.getTransactionTimeZone());
        result.setPaymentMethod(sourceOrder.getPaymentMethod());
        result.setPaymentBrand(sourceOrder.getPaymentBrand());
        result.setTotalAuthorizedAmount(sourceOrder.getLabelAuthorizedAmount());
        result.setTotalCapturedAmount(sourceOrder.getLabelCapturedAmount());
        result.setTotalAuthorizedCancelAmount(sourceOrder.getLabelAuthorizedCancelAmount());
        result.setTotalRefundAmount(sourceOrder.getLabelRefundedAmount());
        result.setTotalRefuseAmount(sourceOrder.getLabelChargebackAmount());
        applyChannelStatus(result, invokeResult, response);
        if (response != null) {
            result.setAuthCode(response.getAuthCode());
            result.setAcquirerReferenceNo(response.getAcquirerReferenceNo());
        }
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(result.getStatus())) {
            result.setTotalRefundAmount(zeroIfNull(sourceOrder.getLabelRefundedAmount())
                    .add(zeroIfNull(operation.getLabelAmount())));
        }
        return result;
    }

    /**
     * 应用渠道状态映射结果。
     */
    private void applyChannelStatus(PaymentCreateResultDTO result,
                                    PaymentChannelInvokeResultDTO invokeResult,
                                    ChannelPaymentResponse response) {
        if (isInvokeFailed(invokeResult)) {
            if (invokeResult.isOutcomeUncertain()) {
                // 请求可能已经到达渠道，未知结果必须保持处理中，防止失败重试造成重复退款。
                result.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
                result.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
                result.setPendingReasonCode(PaymentPendingReasonEnum.CHANNEL_RESULT_UNKNOWN.getCode());
            } else {
                // 只有明确拒绝或确认未发送的请求才允许进入失败终态。
                result.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
                result.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
                result.setFailReasonCode(confirmedFailureReason(invokeResult));
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

    /**
     * 判断渠道调用是否明确失败。
     */
    private boolean isInvokeFailed(PaymentChannelInvokeResultDTO invokeResult) {
        return invokeResult != null
                && (StringUtils.hasText(invokeResult.getExceptionType())
                || "FAILED".equals(invokeResult.getRequestStatus())
                || "TIMEOUT".equals(invokeResult.getRequestStatus()));
    }

    /**
     * 退款执行只允许以渠道明确拒绝或发送前故障形成失败终态。
     */
    private String confirmedFailureReason(PaymentChannelInvokeResultDTO invokeResult) {
        return PaymentFailureReasonEnum.CHANNEL_DECLINED.getCode().equals(invokeResult.getFailureReasonCode())
                ? PaymentFailureReasonEnum.CHANNEL_DECLINED.getCode()
                : PaymentFailureReasonEnum.CHANNEL_REQUEST_NOT_SENT.getCode();
    }

    /**
     * 构建幂等键。
     */
    private String buildIdempotencyKey(TransactionOperationDO operation) {
        String merchantOperationNo = operation.getSourceTransactionId() + ":" + operation.getMerchantOperationNo();
        return idempotencyService.buildTransactionOperationKey(
                operation.getMerchantId(), merchantOperationNo, PaymentTransactionTypeEnum.REFUND.getCode());
    }

    /**
     * 按准备阶段冻结的 ISO 4217 小数位把主币种金额转换为最小货币单位。
     *
     * <p>使用 {@link BigDecimal#longValueExact()} 拒绝非整数最小单位和溢出，禁止静默截断资金金额。</p>
     */
    private Long toMinorAmount(BigDecimal amount, Integer exponent) {
        if (amount == null || exponent == null || exponent < 0) {
            return null;
        }
        return amount.movePointRight(exponent).longValueExact();
    }

    /**
     * 将空金额转换为零值。
     */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 校验已审批退款动作及其原交易事实。
     */
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
