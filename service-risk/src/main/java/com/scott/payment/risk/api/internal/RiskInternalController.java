package com.scott.payment.risk.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.application.RiskEvaluationApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskInternalController
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控内部服务接口，位于 service-risk 接口层，为 service-payment 提供路由前实时风控评估。
 * @status : create
 */
@RestController
@RequestMapping("/internal/risk")
public class RiskInternalController {

    /**
     * 风控评估应用服务。
     */
    private final RiskEvaluationApplicationService riskEvaluationApplicationService;

    /**
     * 创建风控内部服务接口。
     *
     * @param riskEvaluationApplicationService 风控评估应用服务
     */
    public RiskInternalController(RiskEvaluationApplicationService riskEvaluationApplicationService) {
        this.riskEvaluationApplicationService = riskEvaluationApplicationService;
    }

    /**
     * 执行收单支付路由前风控评估。
     *
     * @param requestDTO 支付风控评估请求
     * @return 风控决策结果
     */
    @PostMapping("/evaluate/payment")
    public CommonResult<RiskPaymentEvaluateResultDTO> evaluatePayment(@Valid @RequestBody RiskPaymentEvaluateRequestDTO requestDTO) {
        return success(riskEvaluationApplicationService.evaluatePayment(requestDTO));
    }
}
