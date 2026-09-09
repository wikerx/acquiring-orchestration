package com.scott.payment.risk.repository;

import com.scott.payment.risk.domain.PaymentTransactionLookupResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskPaymentTransactionStatusRepository
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控侧只读查询 payment 交易状态。
 * @status : create
 */
public interface RiskPaymentTransactionStatusRepository {

    /**
     * 从支付事实表读取交易状态，并区分 FOUND、ABSENT 与查询失败 UNKNOWN。
     *
     * @param transactionId 平台交易号
     * @param beginTime 交易所属业务周期开始时间，包含
     * @param endTimeExclusive 交易所属业务周期结束时间，不包含
     * @return 三态查询结果
     */
    PaymentTransactionLookupResult findStatus(
            String transactionId,
            java.time.LocalDateTime beginTime,
            java.time.LocalDateTime endTimeExclusive);
}
