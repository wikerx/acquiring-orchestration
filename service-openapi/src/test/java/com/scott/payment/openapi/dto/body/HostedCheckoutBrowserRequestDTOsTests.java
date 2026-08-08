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
    void shouldRejectInvalidCardEnvelopeBeforeCallingPaymentService() {
        HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO = validSubmitRequest();
        requestDTO.getCardDataEnvelope().setEncryptedKey("not base64+");
        requestDTO.getCardDataEnvelope().setIv("short");
        requestDTO.getCardDataEnvelope().setCiphertext("ciphertext+");
        requestDTO.getCardDataEnvelope().setNonce("short");
        requestDTO.getBillingCardHolderInfo().setEmail("payer");
        requestDTO.getBillingCardHolderInfo().setCountry("US");

        Set<ConstraintViolation<HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest>> violations =
                validator.validate(requestDTO);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains(
                        "cardDataEnvelope.encryptedKey format does not match",
                        "cardDataEnvelope.iv format does not match",
                        "cardDataEnvelope.ciphertext format does not match",
                        "cardDataEnvelope.nonce format does not match",
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

        HostedCheckoutBrowserRequestDTOs.CardDataEnvelopeDTO envelope =
                new HostedCheckoutBrowserRequestDTOs.CardDataEnvelopeDTO();
        envelope.setAlgorithm("RSA-OAEP-256+A256GCM");
        envelope.setKeyId("checkout-card-v1");
        envelope.setEncryptedKey("A".repeat(342));
        envelope.setIv("A".repeat(16));
        envelope.setCiphertext("A".repeat(64));
        envelope.setNonce("A".repeat(32));
        requestDTO.setCardDataEnvelope(envelope);

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
