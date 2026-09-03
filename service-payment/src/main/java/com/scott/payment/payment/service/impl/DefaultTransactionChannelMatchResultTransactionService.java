package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.TransactionChannelMatchResultTransactionService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionChannelMatchResultTransactionService
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 主动渠道查询结果事务默认实现，位于 service-payment 服务实现层，使用 REQUIRES_NEW 保存查询结果并通过 CAS 推进平台状态。
 * @status : create
 */
@Service
public class DefaultTransactionChannelMatchResultTransactionService implements TransactionChannelMatchResultTransactionService {

    private final TransactionRecordService transactionRecordService;

    /**
     * 交易状态变更 Outbox 服务，与渠道补匹配结果在同一数据库事务内写入。
     */
    private final TransactionLifecycleEventService lifecycleEventService;

    /**
     * 创建带终态 Outbox 能力的渠道补匹配结果事务服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param lifecycleEventService    交易状态变更 Outbox 服务
     */
    public DefaultTransactionChannelMatchResultTransactionService(
            TransactionRecordService transactionRecordService,
            TransactionLifecycleEventService lifecycleEventService) {
        this.transactionRecordService = transactionRecordService;
        this.lifecycleEventService = lifecycleEventService;
    }

    /**
     * 在独立事务中保存主动查询确认的终态结果。
     *
     * @param operationDO 原交易动作单
     * @param originalRequestDO 原资金动作渠道请求记录
     * @param invokeResultDTO 渠道查询调用结果
     * @param resolution 渠道状态解析结果
     * @param matchTime 本次查询时间
     * @return true 表示终态推进成功
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean completeByQuery(TransactionOperationDO operationDO,
                                   TransactionChannelRequestDO originalRequestDO,
                                   PaymentChannelInvokeResultDTO invokeResultDTO,
                                   ChannelTransactionStatusResolution resolution,
                                   LocalDateTime matchTime) {
        transactionRecordService.updateOriginalChannelRequestByQuery(
                operationDO,
                originalRequestDO,
                invokeResultDTO,
                resolution == null ? null : resolution.getTargetStatus(),
                resolution == null ? null : resolution.getFailReasonCode());
        TransactionOrderDO orderDO = transactionRecordService.findOrder(operationDO.getTransactionDateTime(), operationDO.getOperationId());
        boolean statusChanged = transactionRecordService.completeByChannelCallback(
                operationDO,
                orderDO,
                originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                resolution == null ? null : resolution.getTargetStatus(),
                resolution == null ? null : resolution.getFailReasonCode(),
                resolution == null ? null : resolution.getFailReasonMessage(),
                resolution == null ? null : resolution.getChannelStatus(),
                resolution == null ? null : resolution.getChannelResponseCode(),
                resolution == null ? null : resolution.getChannelResponseMessage());
        if (statusChanged && resolution != null) {
            lifecycleEventService.saveStatusChanged(
                    operationDO.getTransactionId(),
                    operationDO.getOperationId(),
                    operationDO.getMerchantId(),
                    operationDO.getMerchantOrderNo(),
                    operationDO.getTransactionType(),
                    resolution.getTargetStatus(),
                    operationDO.getTransactionDateTime());
        }
        return statusChanged;
    }

    /** 保存平台终态交易的渠道查询摘要，不进入交易状态机。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean updateTerminalByQuery(TransactionOperationDO operationDO,
                                         TransactionChannelRequestDO originalRequestDO,
                                         PaymentChannelInvokeResultDTO invokeResultDTO,
                                         String matchStatus,
                                         String matchResult,
                                         LocalDateTime matchTime,
                                         String failReason) {
        transactionRecordService.updateOriginalChannelRequestByQuery(
                operationDO,
                originalRequestDO,
                invokeResultDTO,
                matchResult,
                failReason);
        return transactionRecordService.updateTerminalChannelMatch(
                operationDO,
                matchStatus,
                matchResult,
                originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                matchTime,
                null,
                failReason);
    }

    /**
     * 在独立事务中记录主动查询仍需恢复的结果。
     *
     * @param operationDO 原交易动作单
     * @param originalRequestDO 原资金动作渠道请求记录，可为空
     * @param invokeResultDTO 渠道查询调用结果，可为空
     * @param matchResult 勾兑摘要
     * @param matchTime 本次查询时间
     * @param nextMatchTime 下次查询时间
     * @param failReason 查询失败或不可查询原因
     * @return true 表示待恢复摘要更新成功
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markPendingByQuery(TransactionOperationDO operationDO,
                                      TransactionChannelRequestDO originalRequestDO,
                                      PaymentChannelInvokeResultDTO invokeResultDTO,
                                      String matchResult,
                                      LocalDateTime matchTime,
                                      LocalDateTime nextMatchTime,
                                      String failReason) {
        return markPendingByQuery(operationDO, originalRequestDO, invokeResultDTO,
                "PENDING", matchResult, matchTime, nextMatchTime, failReason);
    }

    /** 保存 PENDING、MISMATCHED 或 FAILED 勾兑摘要。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markPendingByQuery(TransactionOperationDO operationDO,
                                      TransactionChannelRequestDO originalRequestDO,
                                      PaymentChannelInvokeResultDTO invokeResultDTO,
                                      String matchStatus,
                                      String matchResult,
                                      LocalDateTime matchTime,
                                      LocalDateTime nextMatchTime,
                                      String failReason) {
        LocalDateTime persistedNextMatchTime = "PENDING".equals(matchStatus) ? nextMatchTime : null;
        transactionRecordService.updateOriginalChannelRequestByQuery(
                operationDO,
                originalRequestDO,
                invokeResultDTO,
                matchResult,
                failReason);
        if (isTerminal(operationDO)) {
            return transactionRecordService.updateTerminalChannelMatch(
                    operationDO,
                    matchStatus,
                    matchResult,
                    originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                    matchTime,
                    persistedNextMatchTime,
                    failReason);
        }
        return transactionRecordService.updateChannelMatch(
                operationDO,
                matchStatus,
                matchResult,
                originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                matchTime,
                persistedNextMatchTime,
                failReason);
    }

    private boolean isTerminal(TransactionOperationDO operationDO) {
        return operationDO != null
                && (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(operationDO.getTransactionStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(operationDO.getTransactionStatus()));
    }
}
