package com.scott.payment.risk.application;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.service.RiskEvaluationService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationApplicationService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控评估应用服务，位于 service-risk 应用编排层，负责内部接口到领域风控服务的用例编排边界。
 * @status : create
 */
@Service
public class RiskEvaluationApplicationService {

    /**
     * 风控评估领域服务。
     */
    private final RiskEvaluationService riskEvaluationService;

    /**
     * 创建风控评估应用服务。
     *
     * @param riskEvaluationService 风控评估领域服务
     */
    public RiskEvaluationApplicationService(RiskEvaluationService riskEvaluationService) {
        this.riskEvaluationService = riskEvaluationService;
    }

    /**
     * 执行收单支付路由前风控评估。
     *
     * @param requestDTO 支付风控评估请求
     * @return 风控决策结果
     */
    public RiskPaymentEvaluateResultDTO evaluatePayment(RiskPaymentEvaluateRequestDTO requestDTO) {
        return riskEvaluationService.evaluatePayment(requestDTO);
    }
}
