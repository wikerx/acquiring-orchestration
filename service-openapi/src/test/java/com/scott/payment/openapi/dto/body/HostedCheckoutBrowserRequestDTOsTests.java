package com.scott.payment.openapi.dto.body;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hosted Checkout 浏览器请求 DTO 校验测试。
 */
class HostedCheckoutBrowserRequestDTOsTests {

    /** 测试类共享的 Bean Validation 工厂，在全部用例结束后统一关闭。 */
    private static ValidatorFactory validatorFactory;

    /** 用于验证浏览器请求 DTO 字段约束的 Validator。 */
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void shouldRejectInvalidCardPaymentSubmitRequestBeforeCallingPaymentService() {
        HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO = validSubmitRequest();
        requestDTO.getCardInfo().setCardNo("4111 1111 1111 1111");
        requestDTO.getCardInfo().setExpirationMonth("13");
        requestDTO.getCardInfo().setExpirationYear("29");
        requestDTO.getCardInfo().setSecurityCode("12");
        requestDTO.getBillingCardHolderInfo().setEmail("payer");
        requestDTO.getBillingCardHolderInfo().setCountry("US");

        Set<ConstraintViolation<HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest>> violations =
                validator.validate(requestDTO);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains(
                        "cardInfo.cardNo format does not match",
                        "cardInfo.expirationMonth format does not match",
                        "cardInfo.expirationYear format does not match",
                        "cardInfo.securityCode format does not match",
                        "billingCardHolderInfo.email format does not match",
                        "billingCardHolderInfo.country format does not match"
                );
    }

    @Test
    void shouldAcceptValidCardPaymentSubmitRequest() {
        Set<ConstraintViolation<HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest>> violations =
                validator.validate(validSubmitRequest());

        assertThat(violations).isEmpty();
    }

    private HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest validSubmitRequest() {
        HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO =
                new HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest();
        requestDTO.setOpaqueToken("opaque-token");
        requestDTO.setCheckoutSessionId("2607271200000000000010");
        requestDTO.setAttemptRequestId("ATTEMPT-001");
        requestDTO.setPaymentMethod("BANK_CARD");

        HostedCheckoutBrowserRequestDTOs.CardInfoDTO cardInfo = new HostedCheckoutBrowserRequestDTOs.CardInfoDTO();
        cardInfo.setCardNo("4111111111111111");
        cardInfo.setExpirationMonth("09");
        cardInfo.setExpirationYear("2029");
        cardInfo.setSecurityCode("123");
        cardInfo.setCardholderName("Payer Example");
        requestDTO.setCardInfo(cardInfo);

        HostedCheckoutBrowserRequestDTOs.BillingCardHolderInfoDTO billing =
                new HostedCheckoutBrowserRequestDTOs.BillingCardHolderInfoDTO();
        billing.setFirstName("Payer");
        billing.setLastName("Example");
        billing.setEmail("payer@example.com");
        billing.setCountry("USA");
        requestDTO.setBillingCardHolderInfo(billing);
        return requestDTO;
    }
}
