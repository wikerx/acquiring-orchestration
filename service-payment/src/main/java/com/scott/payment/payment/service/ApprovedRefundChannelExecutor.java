package com.scott.payment.payment.service;

import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApprovedRefundChannelExecutor
 * @date : 2026-08-06 00:00
 * @description : 已批准退款渠道执行边界，使用固定动作、固定渠道请求身份和审批消息恢复执行上下文。
 * @status : create
 */
public interface ApprovedRefundChannelExecutor {

    /**
     * 发起已通过审批且已完成状态抢占的单笔退款。
     *
     * @param operationDO 已推进到 CHANNEL_REQUESTING 的退款动作
     * @param requestDO 申请受理时持久化的 INIT 渠道请求
     * @param message 审批执行消息
     */
    void execute(TransactionOperationDO operationDO,
                 TransactionChannelRequestDO requestDO,
                 RefundExecutionMessage message);
}
