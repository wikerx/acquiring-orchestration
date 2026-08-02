package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.TransactionChannelMatchResultTransactionService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
@DS(DataSourceName.TRANSACTION)
public class DefaultTransactionChannelMatchResultTransactionService implements TransactionChannelMatchResultTransactionService {

    /**
     * transaction Record Service 依赖，用于 Default Transaction Channel Match Result Transaction Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * 交易状态变更 Outbox 服务，与渠道补匹配结果在同一数据库事务内写入。
     */
    private final TransactionLifecycleEventService lifecycleEventService;

    /**
     * 创建主动渠道查询结果事务默认实现。
     *
     * @param transactionRecordService 交易事实记录服务
     */
    public DefaultTransactionChannelMatchResultTransactionService(TransactionRecordService transactionRecordService) {
        this(transactionRecordService, null);
    }

    /**
     * 创建带终态 Outbox 能力的渠道补匹配结果事务服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param lifecycleEventService    交易状态变更 Outbox 服务
     */
    @Autowired
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
        if (statusChanged && lifecycleEventService != null && resolution != null) {
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markPendingByQuery(TransactionOperationDO operationDO,
                                      TransactionChannelRequestDO originalRequestDO,
                                      PaymentChannelInvokeResultDTO invokeResultDTO,
                                      String matchResult,
                                      LocalDateTime matchTime,
                                      LocalDateTime nextMatchTime,
                                      String failReason) {
        transactionRecordService.updateOriginalChannelRequestByQuery(
                operationDO,
                originalRequestDO,
                invokeResultDTO,
                matchResult,
                failReason);
        return transactionRecordService.updateChannelMatch(
                operationDO,
                "PENDING",
                matchResult,
                originalRequestDO == null ? null : originalRequestDO.getRequestId(),
                matchTime,
                nextMatchTime,
                failReason);
    }
}
