package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;

/**
 * 收单交易风控调用服务，位于 service-payment 服务层，用于隔离支付核心编排与实时风控服务调用，后续对接 service-risk。
 */
public interface PaymentRiskInvokeService {

    /**
     * 执行路由前风控检查。
     *
     * @param commandDTO 创建交易命令
     * @return 风控决策
     */
    PaymentRiskDecisionDTO checkPreRoute(PaymentCreateCommandDTO commandDTO);

    /**
     * 支付本地事务失败后补偿撤销商户累计限额预占。
     *
     * @param commandDTO 支付创建命令
     * @param decisionDTO 风控决策
     * @param reason 补偿原因
     */
    default void cancelMerchantLimitReservation(PaymentCreateCommandDTO commandDTO,
                                                PaymentRiskDecisionDTO decisionDTO,
                                                String reason) {
    }
}
