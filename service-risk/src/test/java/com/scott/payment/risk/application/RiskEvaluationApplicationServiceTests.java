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
 * @description : RiskEvaluationApplicationServiceTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 风控服务层，输入输出边界由所在包和公开方法契约限定。
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
