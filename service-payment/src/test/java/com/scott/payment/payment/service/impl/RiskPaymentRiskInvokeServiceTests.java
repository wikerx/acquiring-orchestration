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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskPaymentRiskInvokeServiceTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : Risk Payment Risk Invoke Service Tests 自动化测试类，位于 支付核心服务，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class RiskPaymentRiskInvokeServiceTests {

    @Test
    void shouldSendFullPanOnlyToInternalRiskService() {
        CapturingRiskInternalClient riskInternalClient = new CapturingRiskInternalClient(PaymentRiskDecisionEnum.PASS.getCode());
        RiskPaymentRiskInvokeService service = new RiskPaymentRiskInvokeService(riskInternalClient);
        PaymentCreateCommandDTO commandDTO = baseCommand();
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCreateCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("4111-1111 1111-1234");
        cardInfoDTO.setSecurityCode("123");
        commandDTO.setCardInfo(cardInfoDTO);
        PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingInfoDTO = new PaymentCreateCommandDTO.BillingCardHolderInfoDTO();
        billingInfoDTO.setEmail("buyer@example.test");
        billingInfoDTO.setPhone("+1 555 0100");
        billingInfoDTO.setFirstName("John");
        billingInfoDTO.setLastName("Smith");
        billingInfoDTO.setStreet("1 Billing Street");
        billingInfoDTO.setPostal("10001");
        billingInfoDTO.setCountry("USA");
        billingInfoDTO.setState("NY");
        billingInfoDTO.setCity("New York");
        commandDTO.setBillingCardHolderInfo(billingInfoDTO);
        PaymentCreateCommandDTO.PayerInfoDTO payerInfoDTO = new PaymentCreateCommandDTO.PayerInfoDTO();
        payerInfoDTO.setPayerId("CUSTOMER-002");
        payerInfoDTO.setFirstName("Payer");
        payerInfoDTO.setLastName("Person");
        payerInfoDTO.setEmail("payer@example.test");
        payerInfoDTO.setPhone("+1 555 0200");
        payerInfoDTO.setCountry("CAN");
        payerInfoDTO.setState("ON");
        payerInfoDTO.setCity("Toronto");
        payerInfoDTO.setStreet("9 Payer Street");
        payerInfoDTO.setPostal("M5V 1A1");
        payerInfoDTO.setIpAddress("203.0.113.10");
        payerInfoDTO.setSessionId("SESSION-002");
        commandDTO.setPayerInfo(payerInfoDTO);
        PaymentCreateCommandDTO.ShippingInfoDTO shippingInfoDTO = new PaymentCreateCommandDTO.ShippingInfoDTO();
        shippingInfoDTO.setFirstName("Receiver");
        shippingInfoDTO.setLastName("Person");
        shippingInfoDTO.setEmail("receiver@example.test");
        shippingInfoDTO.setPhone("+1 555 0300");
        shippingInfoDTO.setCountry("GBR");
        shippingInfoDTO.setState("London");
        shippingInfoDTO.setCity("London");
        shippingInfoDTO.setStreet("3 Shipping Street");
        shippingInfoDTO.setPostal("SW1A 1AA");
        commandDTO.setShippingInfo(shippingInfoDTO);
        PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchantInfoDTO = new PaymentCreateCommandDTO.SubMerchantInfoDTO();
        subMerchantInfoDTO.setSubId("SUB-001");
        subMerchantInfoDTO.setSubName("Jane Owner");
        subMerchantInfoDTO.setSubCompanyName("Example Trading Limited");
        subMerchantInfoDTO.setSubStreet("100 Merchant Street");
        commandDTO.setSubMerchantInfo(subMerchantInfoDTO);
        PaymentCreateCommandDTO.RiskContextDTO riskContextDTO = new PaymentCreateCommandDTO.RiskContextDTO();
        riskContextDTO.setCustomerId("CUSTOMER-001");
        riskContextDTO.setDeviceFingerprint("DEVICE-FP-001");
        riskContextDTO.setShippingAddress("2 Shipping Street");
        riskContextDTO.setShippingPostalCode("10003");
        riskContextDTO.setShippingCountry("USA");
        commandDTO.setRiskContext(riskContextDTO);

        PaymentRiskDecisionDTO decisionDTO = service.checkPreRoute(commandDTO);

        assertThat(decisionDTO.isPassed()).isTrue();
        assertThat(riskInternalClient.requestDTO.getCardNo()).isEqualTo("4111111111111234");
        assertThat(riskInternalClient.requestDTO.getCardBin()).isEqualTo("41111111111");
        assertThat(riskInternalClient.requestDTO.getCardLast4()).isEqualTo("1234");
        assertThat(riskInternalClient.requestDTO.getBillingPhone()).isEqualTo("+1 555 0100");
        assertThat(riskInternalClient.requestDTO.getCardholderName()).isEqualTo("John Smith");
        assertThat(riskInternalClient.requestDTO.getLegalPerson()).isEqualTo("Jane Owner");
        assertThat(riskInternalClient.requestDTO.getEnterprise()).isEqualTo("Example Trading Limited");
        assertThat(riskInternalClient.requestDTO.getMerchantBillingAddress()).isEqualTo("100 Merchant Street");
        assertThat(riskInternalClient.requestDTO.getBillingAddress()).isEqualTo("1 Billing Street");
        assertThat(riskInternalClient.requestDTO.getBillingZip()).isEqualTo("10001");
        assertThat(riskInternalClient.requestDTO.getBillingCountry()).isEqualTo("USA");
        assertThat(riskInternalClient.requestDTO.getBillingRegion()).isEqualTo("NY");
        assertThat(riskInternalClient.requestDTO.getBillingCity()).isEqualTo("New York");
        assertThat(riskInternalClient.requestDTO.getPayerId()).isEqualTo("CUSTOMER-002");
        assertThat(riskInternalClient.requestDTO.getPayerEmail()).isEqualTo("payer@example.test");
        assertThat(riskInternalClient.requestDTO.getPayerAddress()).isEqualTo("9 Payer Street");
        assertThat(riskInternalClient.requestDTO.getPayerCountry()).isEqualTo("CAN");
        assertThat(riskInternalClient.requestDTO.getPayerIp()).isEqualTo("203.0.113.10");
        assertThat(riskInternalClient.requestDTO.getCustomerId()).isEqualTo("CUSTOMER-001");
        assertThat(riskInternalClient.requestDTO.getDeviceFingerprint()).isEqualTo("DEVICE-FP-001");
        assertThat(riskInternalClient.requestDTO.getShippingAddress()).isEqualTo("3 Shipping Street");
        assertThat(riskInternalClient.requestDTO.getShippingZip()).isEqualTo("SW1A 1AA");
        assertThat(riskInternalClient.requestDTO.getShippingCountry()).isEqualTo("GBR");
        assertThat(riskInternalClient.requestDTO.getShippingEmail()).isEqualTo("receiver@example.test");
        assertThat(RiskPaymentEvaluateClientRequestDTO.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("securityCode", "cvv");
    }

    @Test
    void shouldDenyUnknownRiskDecision() {
        CapturingRiskInternalClient riskInternalClient = new CapturingRiskInternalClient("UNKNOWN_REMOTE_DECISION");
        RiskPaymentRiskInvokeService service = new RiskPaymentRiskInvokeService(riskInternalClient);

        PaymentRiskDecisionDTO decisionDTO = service.checkPreRoute(baseCommand());

        assertThat(decisionDTO.isPassed()).isFalse();
        assertThat(decisionDTO.getDecision()).isEqualTo(PaymentRiskDecisionEnum.UNKNOWN.getCode());
    }

    @Test
    void shouldFailClosedWhenRiskServiceThrowsWithoutLeakingTheExceptionMessage() {
        RiskPaymentRiskInvokeService service = new RiskPaymentRiskInvokeService(requestDTO -> {
            throw new IllegalStateException("select match_value_hash from risk_black_region failed");
        });
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setTransactionId("TX202607290001");

        PaymentRiskDecisionDTO decisionDTO = service.checkPreRoute(commandDTO);

        assertThat(decisionDTO.isPassed()).isFalse();
        assertThat(decisionDTO.getDecision()).isEqualTo(PaymentRiskDecisionEnum.UNKNOWN.getCode());
        assertThat(decisionDTO.getRiskCode()).isEqualTo("RISK_SERVICE_UNAVAILABLE");
        assertThat(decisionDTO.getRiskMessage())
                .isEqualTo("risk service is unavailable")
                .doesNotContain("match_value_hash", "risk_black_region");
        assertThat(commandDTO.getRiskCode()).isEqualTo("RISK_SERVICE_UNAVAILABLE");
        assertThat(commandDTO.getRiskMessage()).isEqualTo("risk service is unavailable");
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

        /**
         * 捕获支付服务提交的风控请求，并返回由当前用例指定的确定性风控结论。
         */
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
