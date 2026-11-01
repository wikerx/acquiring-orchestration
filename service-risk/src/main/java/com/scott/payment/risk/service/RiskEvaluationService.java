package com.scott.payment.risk.service;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;

/**
 * 风控evaluation服务契约，位于 风控服务，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
 */
public interface RiskEvaluationService {

    /**
     * 执行收单支付路由前风控评估。
     *
     * @param requestDTO 支付风控评估请求
     * @return 风控决策结果
     */
    RiskPaymentEvaluateResultDTO evaluatePayment(RiskPaymentEvaluateRequestDTO requestDTO);
}
