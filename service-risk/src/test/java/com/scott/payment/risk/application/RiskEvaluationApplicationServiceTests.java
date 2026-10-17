package com.scott.payment.risk.application;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.service.RiskEvaluationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationApplicationServiceTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : 风控evaluation应用服务，位于 风控服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
class RiskEvaluationApplicationServiceTests {

    @Test
    void shouldDelegatePaymentRiskEvaluation() {
        RiskEvaluationService evaluationService = mock(RiskEvaluationService.class);
        RiskEvaluationApplicationService applicationService = new RiskEvaluationApplicationService(evaluationService);
        RiskPaymentEvaluateRequestDTO requestDTO = new RiskPaymentEvaluateRequestDTO();
        requestDTO.setMerchantId("200001");
        requestDTO.setMerchantOrderNo("M202607120002");
        requestDTO.setAmount(new BigDecimal("10.00"));
        requestDTO.setCurrency("USD");
        RiskPaymentEvaluateResultDTO expected = new RiskPaymentEvaluateResultDTO();
        expected.setDecision(RiskDecisionEnum.SKIP.getCode());
        when(evaluationService.evaluatePayment(requestDTO)).thenReturn(expected);

        assertThat(applicationService.evaluatePayment(requestDTO)).isSameAs(expected);
    }
}
