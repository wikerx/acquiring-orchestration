package com.scott.payment.openapi.dto.body;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiMerchantPaymentRequestDTOTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Merchant payment request contract validation tests.
 * @status : create
 */
class ApiMerchantPaymentRequestDTOTests {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void shouldAllowQueryWithoutTransactionInfo() {
        ApiMerchantPaymentRequestDTO request = requestWithMerchantAndOrder();

        Set<ConstraintViolation<ApiMerchantPaymentRequestDTO>> violations = VALIDATOR.validate(
                request,
                ApiMerchantPaymentRequestDTO.Query.class,
                ApiMerchantPaymentRequestDTO.Format.class
        );

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectAmountAndCurrencyForVoid() {
        ApiMerchantPaymentRequestDTO request = requestWithMerchantAndOrder();
        request.getOrderInfo().setAmount(new BigDecimal("120.00"));
        request.getOrderInfo().setCurrency("USD");
        ApiMerchantPaymentRequestDTO.TransactionInfoDTO transactionInfo =
                new ApiMerchantPaymentRequestDTO.TransactionInfoDTO();
        transactionInfo.setSourceTransactionId("202608011130001230007");
        request.setTransactionInfo(transactionInfo);

        Set<ConstraintViolation<ApiMerchantPaymentRequestDTO>> violations = VALIDATOR.validate(
                request,
                ApiMerchantPaymentRequestDTO.AuthorizationCancel.class,
                ApiMerchantPaymentRequestDTO.Format.class
        );

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("orderInfo.amount and orderInfo.currency are not accepted for void");
    }

    @Test
    void shouldLimitMerchantIdToSixThroughSixteenDigits() {
        ApiMerchantPaymentRequestDTO.MerchantInfoDTO merchantInfo =
                new ApiMerchantPaymentRequestDTO.MerchantInfoDTO();
        merchantInfo.setMerchantId("2999999999999999");

        assertThat(VALIDATOR.validate(merchantInfo, ApiMerchantPaymentRequestDTO.Format.class)).isEmpty();

        merchantInfo.setMerchantId("29999999999999999");
        assertThat(VALIDATOR.validate(merchantInfo, ApiMerchantPaymentRequestDTO.Format.class)).isNotEmpty();
    }

    @Test
    void shouldAllowRegisteredSubMerchantReferenceWithThirtyTwoCharacterId() {
        ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo =
                new ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO();
        subMerchantInfo.setSubId("SUBMERCHANT12345678901234567890");

        Set<ConstraintViolation<ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO>> violations = VALIDATOR.validate(
                subMerchantInfo,
                ApiMerchantPaymentRequestDTO.Payment.class,
                ApiMerchantPaymentRequestDTO.Format.class
        );

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptTwoDigitEci() {
        ApiMerchantPaymentRequestDTO.ThreeDsInfoDTO threeDsInfo =
                new ApiMerchantPaymentRequestDTO.ThreeDsInfoDTO();
        threeDsInfo.setEci("05");

        assertThat(VALIDATOR.validate(threeDsInfo, ApiMerchantPaymentRequestDTO.Format.class)).isEmpty();

        threeDsInfo.setEci("005");
        assertThat(VALIDATOR.validate(threeDsInfo, ApiMerchantPaymentRequestDTO.Format.class)).isNotEmpty();
    }

    @Test
    void shouldAcceptCallbackUrlUpToFiveHundredTwelveCharacters() {
        ApiMerchantPaymentRequestDTO.TransactionInfoDTO transactionInfo =
                new ApiMerchantPaymentRequestDTO.TransactionInfoDTO();
        String prefix = "http://merchant.example/";
        transactionInfo.setCallbackUrl(prefix + "a".repeat(512 - prefix.length()));

        assertThat(VALIDATOR.validate(transactionInfo, ApiMerchantPaymentRequestDTO.Format.class)).isEmpty();

        transactionInfo.setCallbackUrl(prefix + "a".repeat(513 - prefix.length()));
        assertThat(VALIDATOR.validate(transactionInfo, ApiMerchantPaymentRequestDTO.Format.class)).isNotEmpty();
    }

    private ApiMerchantPaymentRequestDTO requestWithMerchantAndOrder() {
        ApiMerchantPaymentRequestDTO request = new ApiMerchantPaymentRequestDTO();
        ApiMerchantPaymentRequestDTO.MerchantInfoDTO merchantInfo =
                new ApiMerchantPaymentRequestDTO.MerchantInfoDTO();
        merchantInfo.setMerchantId("200045");
        request.setMerchantInfo(merchantInfo);

        ApiMerchantPaymentRequestDTO.OrderInfoDTO orderInfo = new ApiMerchantPaymentRequestDTO.OrderInfoDTO();
        orderInfo.setOrderNo("M202608010002");
        orderInfo.setOrderId("QUERY202608010001");
        request.setOrderInfo(orderInfo);
        return request;
    }
}
