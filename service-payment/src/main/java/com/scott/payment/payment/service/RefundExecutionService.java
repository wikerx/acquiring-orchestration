package com.scott.payment.payment.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.domain.refund.RefundExecutionOutcomeEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.mapper.TransactionChannelRequestMapper;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundExecutionService
 * @date : 2026-08-06 00:00
 * @description : 退款执行消息状态机，使用数据库 CAS 吸收重复消费，并将任何已有发送事实转为主动 QUERY。
 * @status : create
 */
@Service
@DS(DataSourceName.TRANSACTION)
public class RefundExecutionService {

    private static final String REQUEST_STATUS_INIT = "INIT";

    private final TransactionOperationMapper operationMapper;
    private final TransactionChannelRequestMapper requestMapper;
    private final ApprovedRefundChannelExecutor channelExecutor;
    private final TransactionChannelMatchService channelMatchService;

    /**
     * 创建退款执行消息状态机。
     *
     * @param operationMapper 退款动作 Mapper
     * @param requestMapper 渠道请求 Mapper
     * @param channelExecutor 固定渠道退款执行器
     * @param channelMatchService 主动查询勾兑服务
     */
    public RefundExecutionService(TransactionOperationMapper operationMapper,
                                  TransactionChannelRequestMapper requestMapper,
                                  ApprovedRefundChannelExecutor channelExecutor,
                                  TransactionChannelMatchService channelMatchService) {
        this.operationMapper = operationMapper;
        this.requestMapper = requestMapper;
        this.channelExecutor = channelExecutor;
        this.channelMatchService = channelMatchService;
    }

    /**
     * 消费审批执行消息。
     *
     * <p>只有消息版本与 WAITING_EXECUTION 动作一致，且原渠道请求仍为 INIT，才执行首次渠道退款。
     * CHANNEL_REQUESTING/CHANNEL_PROCESSING 或非 INIT 请求一律主动查询，禁止根据 MQ 重试次数盲目重发。</p>
     *
     * @param message 退款执行消息
     * @return 本次处理结果
     */
    public RefundExecutionOutcomeEnum execute(RefundExecutionMessage message) {
        validate(message);
        TransactionOperationDO operation = operationMapper.selectByTransactionId(
                message.getRefundTransactionId(), message.getRefundTransactionDateTime());
        if (operation == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        if (isTerminal(operation)) {
            return RefundExecutionOutcomeEnum.IGNORED;
        }
        if (isChannelInFlight(operation)) {
            return queryExistingRequest(operation);
        }
        if (!PaymentTransactionStatusEnum.PENDING.getCode().equals(operation.getTransactionStatus())
                || !PaymentProcessStageEnum.WAITING_EXECUTION.getCode().equals(operation.getProcessStage())) {
            return RefundExecutionOutcomeEnum.IGNORED;
        }
        TransactionChannelRequestDO request = requestMapper.selectOriginalByTransaction(
                operation.getTransactionId(), operation.getChannelCode(), operation.getTransactionDateTime());
        if (request == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND.getCode(),
                    "refund channel request fact does not exist");
        }
        if (!REQUEST_STATUS_INIT.equals(request.getRequestStatus())) {
            return queryExistingRequest(operation);
        }
        if (!Objects.equals(message.getExpectedOperationVersion(), operation.getVersion())) {
            return RefundExecutionOutcomeEnum.IGNORED;
        }
        int claimed = operationMapper.claimApprovedRefundExecution(
                operation.getTransactionId(), operation.getTransactionDateTime(), operation.getVersion(), LocalDateTime.now());
        if (claimed != 1) {
            TransactionOperationDO current = operationMapper.selectByTransactionId(
                    operation.getTransactionId(), operation.getTransactionDateTime());
            return current != null && isChannelInFlight(current)
                    ? queryExistingRequest(current)
                    : RefundExecutionOutcomeEnum.IGNORED;
        }
        TransactionOperationDO claimedOperation = operationMapper.selectByTransactionId(
                operation.getTransactionId(), operation.getTransactionDateTime());
        if (claimedOperation == null || !isChannelInFlight(claimedOperation)) {
            throw new ServiceException(ApiResultEnum.REFUND_APPROVAL_STATE_CONFLICT);
        }
        channelExecutor.execute(claimedOperation, request, message);
        return RefundExecutionOutcomeEnum.EXECUTED;
    }

    private RefundExecutionOutcomeEnum queryExistingRequest(TransactionOperationDO operation) {
        channelMatchService.matchOne(operation.getTransactionId(), operation.getTransactionDateTime());
        return RefundExecutionOutcomeEnum.QUERY_TRIGGERED;
    }

    private boolean isChannelInFlight(TransactionOperationDO operation) {
        return PaymentTransactionStatusEnum.PROCESSING.getCode().equals(operation.getTransactionStatus())
                && (PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode().equals(operation.getProcessStage())
                || PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode().equals(operation.getProcessStage()));
    }

    private boolean isTerminal(TransactionOperationDO operation) {
        return PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operation.getTransactionStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(operation.getTransactionStatus());
    }

    private void validate(RefundExecutionMessage message) {
        if (message == null
                || !StringUtils.hasText(message.getMessageId())
                || !StringUtils.hasText(message.getApprovalId())
                || !StringUtils.hasText(message.getRefundTransactionId())
                || message.getRefundTransactionDateTime() == null
                || message.getExpectedOperationVersion() == null
                || !MqTag.REFUND_EXECUTION_REQUESTED.equals(message.getEventType())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }
}
