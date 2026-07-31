package com.scott.payment.payment.service;

import java.time.LocalDateTime;

/**
 * 交易终态生命周期 Outbox 事件服务。
 */
public interface TransactionLifecycleEventService {

    /**
     * 在交易状态写入事务中保存生命周期 Outbox 事件，供风控预占确认或取消消费者幂等处理。
     *
     * @param transactionId       平台交易号
     * @param operationId         本次交易动作号
     * @param merchantId          商户号
     * @param merchantOrderNo     商户订单号
     * @param transactionType     交易类型
     * @param transactionStatus   已持久化的交易状态
     * @param transactionDateTime 交易分表时间，使用支付服务业务时区
     */
    void saveStatusChanged(String transactionId,
                           String operationId,
                           String merchantId,
                           String merchantOrderNo,
                           String transactionType,
                           String transactionStatus,
                           LocalDateTime transactionDateTime);
}
