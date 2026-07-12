package com.scott.payment.risk.application;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.service.impl.DefaultRiskEvaluationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

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
