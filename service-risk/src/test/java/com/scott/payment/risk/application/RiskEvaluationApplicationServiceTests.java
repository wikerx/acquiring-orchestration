package com.scott.payment.risk.application;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.service.impl.DefaultRiskEvaluationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationApplicationServiceTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : Risk Evaluation Application Service Tests 应用服务，位于 风控服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
class RiskEvaluationApplicationServiceTests {

    @Test
    void shouldDelegatePaymentRiskEvaluation() {
        RiskEvaluationApplicationService applicationService = new RiskEvaluationApplicationService(new DefaultRiskEvaluationService());
        RiskPaymentEvaluateRequestDTO requestDTO = new RiskPaymentEvaluateRequestDTO();
        requestDTO.setMerchantId("200001");
        requestDTO.setMerchantOrderNo("M202607120002");
        requestDTO.setAmount(new BigDecimal("10.00"));
        requestDTO.setCurrency("USD");

        assertThat(applicationService.evaluatePayment(requestDTO).getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
    }
}
