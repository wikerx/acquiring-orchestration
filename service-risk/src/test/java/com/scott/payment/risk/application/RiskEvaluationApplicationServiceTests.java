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
 * 风控evaluation应用服务，位于 风控服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 */
class RiskEvaluationApplicationServiceTests {

    /**
     * 验证应用服务原样委派支付风控评估请求和结果。
     */
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
