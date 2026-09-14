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
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutSessionCreateRequestDTOTests
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : Hosted Checkout 会话创建请求回调和跳转 URL 格式校验测试。
 * @status : create
 */
class HostedCheckoutSessionCreateRequestDTOTests {

    private static ValidatorFactory validatorFactory;
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
    void shouldAcceptHttpAndHttpsForPublicPrivateAndLoopbackHostsAtFormatLayer() {
        HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO transactionInfo =
                new HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO();
        transactionInfo.setCallbackUrl("http://192.168.1.10/notify");
        transactionInfo.setRedirectUrl("http://127.0.0.1:5175/result");

        assertThat(validate(transactionInfo)).isEmpty();
    }

    @Test
    void shouldRejectUserInfoUrls() {
        HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO transactionInfo =
                new HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO();
        transactionInfo.setCallbackUrl("https://user:secret@merchant.example/notify");
        transactionInfo.setRedirectUrl("https://user:secret@merchant.example/result");

        assertThat(validate(transactionInfo)).extracting(ConstraintViolation::getMessage)
                .contains(
                        "transactionInfo.callbackUrl format does not match",
                        "transactionInfo.redirectUrl format does not match");
    }

    @Test
    void shouldRejectMalformedOrNonHttpUrls() {
        HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO transactionInfo =
                new HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO();
        transactionInfo.setCallbackUrl("javascript:alert(1)");
        transactionInfo.setRedirectUrl("https:///missing-host");

        assertThat(validate(transactionInfo)).extracting(ConstraintViolation::getMessage)
                .contains(
                        "transactionInfo.callbackUrl format does not match",
                        "transactionInfo.redirectUrl format does not match");
    }

    private Set<ConstraintViolation<HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO>> validate(
            HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO transactionInfo) {
        return validator.validate(transactionInfo, HostedCheckoutSessionCreateRequestDTO.Format.class);
    }
}
