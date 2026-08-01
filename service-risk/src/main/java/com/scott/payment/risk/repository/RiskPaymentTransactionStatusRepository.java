package com.scott.payment.risk.repository;

import com.scott.payment.risk.domain.PaymentTransactionLookupResult;

/**
 * 风控侧只读查询 payment 交易状态。
 */
public interface RiskPaymentTransactionStatusRepository {

    /**
     * 从支付事实表读取交易状态，并区分 FOUND、ABSENT 与查询失败 UNKNOWN。
     *
     * @param transactionId 平台交易号
     * @return 三态查询结果
     */
    PaymentTransactionLookupResult findStatus(String transactionId);
}
