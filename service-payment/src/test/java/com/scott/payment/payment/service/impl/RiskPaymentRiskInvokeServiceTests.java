package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.client.risk.RiskInternalClient;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RiskPaymentRiskInvokeServiceTests {

    @Test
    void shouldSendOnlyCardBinAndLast4ToRiskService() {
        CapturingRiskInternalClient riskInternalClient = new CapturingRiskInternalClient(PaymentRiskDecisionEnum.PASS.getCode());
        RiskPaymentRiskInvokeService service = new RiskPaymentRiskInvokeService(riskInternalClient);
        PaymentCreateCommandDTO commandDTO = baseCommand();
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCreateCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("4111-1111 1111-1234");
        cardInfoDTO.setSecurityCode("123");
        commandDTO.setCardInfo(cardInfoDTO);

        PaymentRiskDecisionDTO decisionDTO = service.checkPreRoute(commandDTO);

        assertThat(decisionDTO.isPassed()).isTrue();
        assertThat(riskInternalClient.requestDTO.getCardBin()).isEqualTo("411111");
        assertThat(riskInternalClient.requestDTO.getCardLast4()).isEqualTo("1234");
        assertThat(RiskPaymentEvaluateClientRequestDTO.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("cardNo", "securityCode", "cvv");
    }

    @Test
    void shouldDenyUnknownRiskDecision() {
        CapturingRiskInternalClient riskInternalClient = new CapturingRiskInternalClient("UNKNOWN_REMOTE_DECISION");
        RiskPaymentRiskInvokeService service = new RiskPaymentRiskInvokeService(riskInternalClient);

        PaymentRiskDecisionDTO decisionDTO = service.checkPreRoute(baseCommand());

        assertThat(decisionDTO.isPassed()).isFalse();
        assertThat(decisionDTO.getDecision()).isEqualTo(PaymentRiskDecisionEnum.UNKNOWN.getCode());
    }

    private PaymentCreateCommandDTO baseCommand() {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202607120001");
        commandDTO.setTransactionType("AUTHORIZATION");
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
        return commandDTO;
    }

    private static class CapturingRiskInternalClient implements RiskInternalClient {

        private final String decision;

        private RiskPaymentEvaluateClientRequestDTO requestDTO;

        private CapturingRiskInternalClient(String decision) {
            this.decision = decision;
        }

        @Override
        public RiskPaymentEvaluateClientResponseDTO evaluatePayment(RiskPaymentEvaluateClientRequestDTO requestDTO) {
            this.requestDTO = requestDTO;
            RiskPaymentEvaluateClientResponseDTO responseDTO = new RiskPaymentEvaluateClientResponseDTO();
            responseDTO.setRiskRecordNo("RK202607120001");
            responseDTO.setDecision(decision);
            responseDTO.setReasonCode("NONE");
            responseDTO.setReasonMessage("risk rule not hit");
            return responseDTO;
        }
    }
}
