package com.scott.payment.risk.service;

import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationLifecycleCoordinator
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额预占终态编排。
 * @status : create
 */
public interface MerchantLimitReservationLifecycleCoordinator {

    /**
     * 将交易全部 RESERVED 预占确认到不可逆 CONFIRMED 终态。
     *
     * @param transactionId 平台交易号
     * @return 应用、幂等和冲突数量
     */
    MerchantLimitReservationTransitionSummary confirm(String transactionId);

    /**
     * 先回滚 Redis 计数，再将非终态记录迁移为 CANCELLED；回滚不确定时不得取消持久记录。
     *
     * @param transactionId 平台交易号
     * @param reason        取消原因
     * @return 应用、幂等和冲突数量
     */
    MerchantLimitReservationTransitionSummary cancel(String transactionId, String reason);

    /**
     * 根据支付终态确认或取消预占；处理中状态保持 RESERVED。
     *
     * @param transactionId 平台交易号
     * @param paymentStatus 支付交易状态
     * @param reason        状态来源说明
     * @return 状态迁移汇总
     */
    MerchantLimitReservationTransitionSummary applyPaymentStatus(String transactionId,
                                                                 String paymentStatus,
                                                                 String reason);
}
