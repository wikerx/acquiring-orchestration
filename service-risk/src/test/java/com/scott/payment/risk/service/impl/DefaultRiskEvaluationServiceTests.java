package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.domain.state.RiskReasonCodeEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRiskEvaluationServiceTests {

    private final DefaultRiskEvaluationService service = new DefaultRiskEvaluationService();

    @Test
    void shouldPassNormalPaymentRiskEvaluation() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NONE.getCode());
        assertThat(resultDTO.getRiskRecordNo()).startsWith("RK");
    }

    @Test
    void shouldRejectBlockedSource() {
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setSourceUrl("https://blocked.example.test/pay");

        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.BLOCKED_SOURCE.getCode());
    }

    @Test
    void shouldRequireThreeDsForLargePaymentWithoutThreeDsProof() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("1000.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REQUIRE_3DS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.THREE_DS_REQUIRED.getCode());
    }

    @Test
    void shouldReviewVeryLargePayment() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("5000.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REVIEW.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.MANUAL_REVIEW_REQUIRED.getCode());
    }

    private RiskPaymentEvaluateRequestDTO baseRequest(BigDecimal amount) {
        RiskPaymentEvaluateRequestDTO requestDTO = new RiskPaymentEvaluateRequestDTO();
        requestDTO.setMerchantId("200001");
        requestDTO.setMerchantOrderNo("M202607120001");
        requestDTO.setAmount(amount);
        requestDTO.setCurrency("USD");
        return requestDTO;
    }
}
