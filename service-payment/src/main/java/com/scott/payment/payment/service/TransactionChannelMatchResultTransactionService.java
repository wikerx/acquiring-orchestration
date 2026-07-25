package com.scott.payment.payment.service;

import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelMatchResultTransactionService
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 主动渠道查询结果事务服务，位于 service-payment 服务层，负责在独立事务中回写原渠道请求并通过 CAS 推进或保留交易状态。
 * @status : create
 */
public interface TransactionChannelMatchResultTransactionService {

    /**
     * 在独立事务中保存主动查询确认的终态结果。
     *
     * @param operationDO 原交易动作单
     * @param originalRequestDO 原资金动作渠道请求记录
     * @param invokeResultDTO 渠道查询调用结果
     * @param resolution 渠道状态解析结果
     * @param matchTime 本次查询时间
     * @return true 表示交易终态 CAS 推进成功
     */
    boolean completeByQuery(TransactionOperationDO operationDO,
                            TransactionChannelRequestDO originalRequestDO,
                            PaymentChannelInvokeResultDTO invokeResultDTO,
                            ChannelTransactionStatusResolution resolution,
                            LocalDateTime matchTime);

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
     * @return true 表示待恢复摘要 CAS 更新成功
     */
    boolean markPendingByQuery(TransactionOperationDO operationDO,
                               TransactionChannelRequestDO originalRequestDO,
                               PaymentChannelInvokeResultDTO invokeResultDTO,
                               String matchResult,
                               LocalDateTime matchTime,
                               LocalDateTime nextMatchTime,
                               String failReason);
}
