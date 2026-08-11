package com.scott.payment.openapi.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.checkout.PaymentCheckoutClientDTOs;
import com.scott.payment.openapi.config.PaymentClientProperties;
import com.scott.payment.openapi.converter.OpenApiRequestConverter;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.enums.OpenApiPaymentOperationEnum;
import com.scott.payment.openapi.support.OpenApiRequestAttributes;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import com.scott.payment.openapi.vo.payment.PaymentQueryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentServiceImplTests
 * @date : 2026-07-14 21:10
 * @email : scott_x@163.com
 * @description : OpenAPI 收单支付服务测试，验证独立交易入口能正确组装内部请求，并避免对外暴露按枚举分发的粗粒度接口。
 * @status : create
 */
class PaymentServiceImplTests {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPassFullAuthorizationContextToPaymentService() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        bindRequestContext();

        paymentService.createAuthorization("encrypted-body", buildRequest());

        PaymentCreateClientRequestDTO captured = paymentInternalClient.requestDTO;
        assertThat(paymentInternalClient.calledOperation).isEqualTo(OpenApiPaymentOperationEnum.AUTHORIZATION);
        assertThat(captured.getMerchantId()).isEqualTo("200001");
        assertThat(captured.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(captured.getMerchantOrderId()).isEqualTo("REQ202607120001");
        assertThat(captured.getRequestId()).isEqualTo("REQ202607120001");
        assertThat(captured.getTransactionType()).isEqualTo("AUTHORIZATION");
        assertThat(captured.getPaymentMethod()).isEqualTo("BANK_CARD");
        assertThat(captured.getCardInfo().getCardNo()).isEqualTo("5387380678556554");
        assertThat(captured.getCardInfo().getSecurityCode()).isEqualTo("123");
        assertThat(captured.getBillingCardHolderInfo().getEmail()).isEqualTo("user@example.com");
        assertThat(captured.getThreeDsInfo().getDsTransactionId()).isEqualTo("b96c957d-daa1-4b7f-b8b4-373fb9dec47b");
        assertThat(captured.getTransactionInfo().getCardBrand()).isNull();
        assertThat(captured.getTransactionInfo().getMerchantWebsite())
                .isEqualTo("https://merchant-shop.example/checkout?cart=ABC123");
        assertThat(captured.getCallbackUrl()).isEqualTo("https://merchant.example/callback");
        assertThat(captured.getSourceUrl()).isEqualTo("https://merchant-shop.example/checkout?cart=ABC123");
        assertThat(captured.getPayerIp()).isEqualTo("198.51.100.10");
        assertThat(captured.getUserAgent()).isEqualTo("JUnit");
        assertThat(captured.getRiskContext().getCustomerId()).isEqualTo("CUSTOMER-001");
        assertThat(captured.getRiskContext().getDeviceFingerprint()).isEqualTo("DEVICE-FP-001");
        assertThat(captured.getRiskContext().getShippingAddress()).isEqualTo("2 Shipping St");
        assertThat(captured.getRiskContext().getShippingPostalCode()).isEqualTo("10003");
        assertThat(captured.getRiskContext().getShippingCountry()).isEqualTo("USA");
    }

    @Test
    void shouldUsePaymentResultAsMerchantWebsiteResponseSource() {
        OpenApiRequestConverter converter = Mappers.getMapper(OpenApiRequestConverter.class);
        ApiMerchantPaymentRequestDTO requestDTO = buildRequest();
        PaymentCreateClientResponseDTO responseDTO = new PaymentCreateClientResponseDTO();

        assertThat(converter.toPaymentCreateVO(requestDTO, responseDTO, null)
                .getTransactionInfo().getMerchantWebsite()).isNull();

        responseDTO.setMerchantWebsite("https://stored-merchant.example/original");
        assertThat(converter.toPaymentCreateVO(requestDTO, responseDTO, null)
                .getTransactionInfo().getMerchantWebsite())
                .isEqualTo("https://stored-merchant.example/original");
    }

    @Test
    void shouldPassCaptureOperationToPaymentService() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        bindRequestContext();

        paymentService.capture("encrypted-body", buildRequest());

        assertThat(paymentInternalClient.calledOperation).isEqualTo(OpenApiPaymentOperationEnum.CAPTURE);
        assertThat(paymentInternalClient.requestDTO.getTransactionType()).isEqualTo("CAPTURE");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderId()).isEqualTo("REQ202607120001");
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionId()).isEqualTo("source-001");
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionDateTime()).isNull();
    }

    @Test
    void shouldPassPreAuthCompletionOperationToPaymentService() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        bindRequestContext();

        paymentService.preAuthCompletion("encrypted-body", buildRequest());

        assertThat(paymentInternalClient.calledOperation).isEqualTo(OpenApiPaymentOperationEnum.PRE_AUTH_COMPLETION);
        assertThat(paymentInternalClient.requestDTO.getTransactionType()).isEqualTo("PRE_AUTH_COMPLETION");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderId()).isEqualTo("REQ202607120001");
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionId()).isEqualTo("source-001");
    }

    /**
     * 服务接口应暴露明确的交易动作方法，不再让应用层传枚举做分发。
     */
    @Test
    void shouldNotExposeSubmitTransactionOnPaymentService() {
        boolean hasSubmitTransaction = Arrays.stream(com.scott.payment.openapi.service.PaymentService.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch("submitTransaction"::equals);

        assertThat(hasSubmitTransaction).isFalse();
    }

    @Test
    void shouldPassQueryOperationWithOptionalTransactionIdToPaymentService() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        ApiMerchantPaymentRequestDTO requestDTO = buildRequest();
        requestDTO.getTransactionInfo().setSourceTransactionId(null);
        requestDTO.getTransactionInfo().setMerchantWebsite(null);
        requestDTO.getTransactionInfo().setTransactionId("202607120001000001");
        requestDTO.getTransactionInfo().setSourceTransactionDateTime(
                OffsetDateTime.of(2026, 7, 12, 2, 30, 0, 123_000_000, ZoneOffset.UTC));
        requestDTO.getTransactionInfo().setRootTransactionDateTime(
                OffsetDateTime.of(2026, 7, 11, 16, 15, 0, 456_000_000, ZoneOffset.UTC));
        bindRequestContext();

        PaymentQueryVO responseVO = paymentService.queryTransaction("encrypted-body", requestDTO);

        assertThat(paymentInternalClient.calledOperation).isEqualTo(OpenApiPaymentOperationEnum.QUERY);
        assertThat(paymentInternalClient.requestDTO.getTransactionType()).isEqualTo("QUERY");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderId()).isEqualTo("REQ202607120001");
        assertThat(paymentInternalClient.requestDTO.getRequestId()).isEqualTo("REQ202607120001");
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionId()).isNull();
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getTransactionId()).isEqualTo("202607120001000001");
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 12, 10, 30, 0, 123_000_000));
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getRootTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 12, 0, 15, 0, 456_000_000));
        assertThat(responseVO.getTransactionInfo()).hasSize(1);
        assertThat(responseVO.getTransactionInfo().get(0).getTransactionId()).isEqualTo("202607120001000001");
        assertThat(responseVO.getTransactionInfo().get(0).getRootTransactionDateTime())
                .isEqualTo(OffsetDateTime.of(2026, 7, 12, 0, 15, 0, 456_000_000, ZoneOffset.ofHours(8)));
        assertThat(responseVO.getTransactionInfo().get(0).getMerchantWebsite())
                .isEqualTo("https://merchant-shop.example/checkout?cart=ABC123");
    }

    @Test
    void shouldSerializeMerchantResponseWithoutNullAndDccEdcFlags() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.createPayment("encrypted-body", buildRequest()));
        Map<String, Object> responseMap = JsonUtils.parseObject(responseJson, new TypeReference<>() {
        });

        assertThat(responseJson).contains("\"merchantInfo\"");
        assertThat(responseJson).contains("\"billingCardHolderInfo\"");
        assertThat(responseJson).contains("\"transactionInfo\"");
        assertThat(responseJson).contains("\"billingInfo\"");
        assertThat(responseJson).contains("\"transactionRate\":1.00000000");
        assertThat(responseJson).contains("\"settlementCurrency\":\"HKD\"");
        assertThat(responseJson).contains("\"currency\":\"USD\"");
        assertThat(responseJson).contains("\"totalAuthorizedAmount\":12.34");
        assertThat(responseJson).contains("\"totalCapturedAmount\":12.34");
        assertThat(responseJson).doesNotContain("\"status\":\"PROCESSING\"");
        assertThat(responseJson).doesNotContain("\"transactionStatus\"");
        assertThat(responseJson).doesNotContain("\"processStage\"");
        assertThat(responseJson).doesNotContain("\"cardInfo\"");
        assertThat(responseJson).contains("\"subEmail\":\"merchant@example.com\"");
        assertThat(responseJson).contains("\"phone\":\"+1-555-0100\"");
        assertThat(responseJson).doesNotContain("operationId");
        assertThat(responseJson).doesNotContain("channelTransactionId");
        assertThat(responseJson).doesNotContain("dccEnabled");
        assertThat(responseJson).doesNotContain("edcEnabled");
        assertThat(responseJson).doesNotContain("failReasonCode");
        assertThat(responseJson).doesNotContain("failReasonMessage");
        assertThat(responseJson).doesNotContain("null");
        assertThat(responseJson).contains("\"cardBrand\":\"MASTERCARD\"");
        assertThat(responseJson).contains("\"merchantWebsite\":\"https://merchant-shop.example/checkout?cart=ABC123\"");
        assertThat(responseMap).containsOnlyKeys("merchantInfo", "orderInfo", "billingCardHolderInfo", "transactionInfo", "billingInfo");
        assertThat(responseMap).doesNotContainKeys("merchantId", "orderNo", "orderId", "transactionId", "status", "currency", "amount");
    }

    @Test
    void shouldReturnUnifiedMessageAndHideInternalReasonForFailedPayment() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        paymentInternalClient.nextPaymentStatus = "FAILED";
        paymentInternalClient.nextMerchantResponseCode = "F210";
        paymentInternalClient.nextMerchantResponseMessage = "Rejected";
        paymentInternalClient.nextFailReasonCode = "CHANNEL_REQUEST_FAILED";
        paymentInternalClient.nextFailReasonMessage = "Unexpected parameter 'authentication.threeDs.acsEci'";
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.createPayment("encrypted-body", buildRequest()));

        assertThat(responseJson).contains("\"code\":\"F210\"");
        assertThat(responseJson).contains("\"message\":\"The transaction was declined; please contact your card issuer or try again.\"");
        assertThat(responseJson).contains("\"totalAuthorizedAmount\":0");
        assertThat(responseJson).contains("\"totalCapturedAmount\":0");
        assertThat(responseJson).doesNotContain("failReasonCode");
        assertThat(responseJson).doesNotContain("failReasonMessage");
        assertThat(responseJson).doesNotContain("Unexpected parameter");
        assertThat(responseJson).doesNotContain("CHANNEL_REQUEST_FAILED");
    }

    @Test
    void shouldReturnChannelDeclineMessageWhenPaymentFailureIsMerchantVisible() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        paymentInternalClient.nextPaymentStatus = "FAILED";
        paymentInternalClient.nextMerchantResponseCode = "F210";
        paymentInternalClient.nextMerchantResponseMessage = "05: Declined";
        paymentInternalClient.nextFailReasonCode = "CHANNEL_DECLINED";
        paymentInternalClient.nextFailReasonMessage = "MPGS declined by issuer";
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.createPayment("encrypted-body", buildRequest()));

        assertThat(responseJson).contains("\"code\":\"F210\"");
        assertThat(responseJson).contains("\"message\":\"05: Declined\"");
        assertThat(responseJson).doesNotContain("MPGS declined by issuer");
        assertThat(responseJson).doesNotContain("CHANNEL_DECLINED");
    }

    @Test
    void shouldEchoMerchantRequestAndNormalizeTotalsForSuccessfulAuthorization() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        paymentInternalClient.nextAuthorizationStatus = "SUCCESS";
        paymentInternalClient.nextMerchantResponseCode = "T200";
        paymentInternalClient.nextMerchantResponseMessage = "Success";
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        ApiMerchantPaymentRequestDTO requestDTO = buildRequest();
        requestDTO.getTransactionInfo().setDescription("authorization merchant memo");
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.createAuthorization("encrypted-body", requestDTO));
        Map<String, Object> responseMap = JsonUtils.parseObject(responseJson, new TypeReference<>() {
        });

        assertThat(responseJson).contains("\"merchantInfo\"");
        assertThat(responseJson).contains("\"subEmail\":\"merchant@example.com\"");
        assertThat(responseJson).contains("\"billingCardHolderInfo\"");
        assertThat(responseJson).contains("\"orderNo\":\"M202607120001\"");
        assertThat(responseJson).contains("\"orderId\":\"REQ202607120001\"");
        assertThat(responseJson).contains("\"totalAuthorizedAmount\":12.34");
        assertThat(responseJson).contains("\"totalCapturedAmount\":0");
        assertThat(responseJson).contains("\"totalRefundAmount\":0");
        assertThat(responseJson).contains("\"code\":\"T200\"");
        assertThat(responseJson).contains("\"message\":\"Success\"");
        assertThat(responseJson).contains("\"transactionType\":\"AUTHORIZATION\"");
        assertThat(responseJson).contains("\"cardBrand\":\"MASTERCARD\"");
        assertThat(responseJson).contains("\"description\":\"authorization merchant memo\"");
        assertThat(responseJson).contains("\"callbackUrl\":\"https://merchant.example/callback\"");
        assertThat(responseJson).contains("\"settlementCurrency\":\"HKD\"");
        assertThat(responseJson).doesNotContain("\"cardInfo\"");
        assertThat(responseJson).doesNotContain("\"transactionStatus\"");
        assertThat(responseJson).doesNotContain("\"processStage\"");
        assertThat(responseMap).containsOnlyKeys("merchantInfo", "orderInfo", "billingCardHolderInfo", "transactionInfo", "billingInfo");
    }

    @Test
    void shouldEchoMerchantRequestAndHideInternalReasonForFailedAuthorization() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        paymentInternalClient.nextAuthorizationStatus = "FAILED";
        paymentInternalClient.nextMerchantResponseCode = "F210";
        paymentInternalClient.nextMerchantResponseMessage = "Rejected";
        paymentInternalClient.nextFailReasonCode = "CHANNEL_REQUEST_FAILED";
        paymentInternalClient.nextFailReasonMessage = "Unexpected parameter 'authentication.threeDs.acsEci'";
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        ApiMerchantPaymentRequestDTO requestDTO = buildRequest();
        requestDTO.getTransactionInfo().setDescription("authorization expected channel failure");
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.createAuthorization("encrypted-body", requestDTO));

        assertThat(responseJson).contains("\"merchantInfo\"");
        assertThat(responseJson).contains("\"subTaxId\":\"TAX001\"");
        assertThat(responseJson).contains("\"billingCardHolderInfo\"");
        assertThat(responseJson).contains("\"orderNo\":\"M202607120001\"");
        assertThat(responseJson).contains("\"orderId\":\"REQ202607120001\"");
        assertThat(responseJson).contains("\"totalAuthorizedAmount\":0");
        assertThat(responseJson).contains("\"totalCapturedAmount\":0");
        assertThat(responseJson).contains("\"totalRefundAmount\":0");
        assertThat(responseJson).contains("\"code\":\"F210\"");
        assertThat(responseJson).contains("\"message\":\"The transaction was declined; please contact your card issuer or try again.\"");
        assertThat(responseJson).contains("\"transactionType\":\"AUTHORIZATION\"");
        assertThat(responseJson).contains("\"cardBrand\":\"MASTERCARD\"");
        assertThat(responseJson).contains("\"description\":\"authorization expected channel failure\"");
        assertThat(responseJson).contains("\"callbackUrl\":\"https://merchant.example/callback\"");
        assertThat(responseJson).contains("\"settlementCurrency\":\"HKD\"");
        assertThat(responseJson).doesNotContain("\"cardInfo\"");
        assertThat(responseJson).doesNotContain("failReasonCode");
        assertThat(responseJson).doesNotContain("failReasonMessage");
        assertThat(responseJson).doesNotContain("Unexpected parameter");
        assertThat(responseJson).doesNotContain("CHANNEL_REQUEST_FAILED");
        assertThat(responseJson).doesNotContain("Channel rejected");
    }

    @Test
    void shouldAllowOptionalSubMerchantInfoInMerchantRequest() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        ApiMerchantPaymentRequestDTO requestDTO = buildRequest();
        requestDTO.getMerchantInfo().setSubMerchantInfo(null);
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.createPayment("encrypted-body", requestDTO));

        assertThat(responseJson).contains("\"merchantInfo\":{\"merchantId\":\"200001\"}");
        assertThat(responseJson).doesNotContain("subMerchantInfo");
        assertThat(paymentInternalClient.requestDTO.getSubMerchantInfo()).isNull();
    }

    @Test
    void shouldAcceptMinimalRefundRequestAndEchoRefundFields() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        paymentInternalClient.nextPaymentStatus = "SUCCESS";
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        ApiMerchantPaymentRequestDTO requestDTO = buildRequest();
        requestDTO.getOrderInfo().setOrderNo(null);
        requestDTO.getOrderInfo().setCurrency(null);
        requestDTO.getBillingCardHolderInfo().setEmail("unused@example.com");
        requestDTO.setBillingCardHolderInfo(null);
        requestDTO.setCardInfo(null);
        requestDTO.setThreeDsInfo(null);
        requestDTO.getTransactionInfo().setSourceTransactionDateTime(
                OffsetDateTime.of(2026, 8, 2, 4, 15, 30, 123_000_000, ZoneOffset.UTC));
        requestDTO.getTransactionInfo().setRootTransactionDateTime(
                OffsetDateTime.of(2026, 8, 1, 2, 30, 0, 456_000_000, ZoneOffset.UTC));
        requestDTO.getTransactionInfo().setDescription("merchant refund note");
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.refund("encrypted-body", requestDTO));

        assertThat(paymentInternalClient.calledOperation).isEqualTo(OpenApiPaymentOperationEnum.REFUND);
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderNo()).isNull();
        assertThat(paymentInternalClient.requestDTO.getCurrency()).isNull();
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionId()).isEqualTo("source-001");
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 2, 12, 15, 30, 123_000_000));
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getRootTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 30, 0, 456_000_000));
        assertThat(responseJson).contains("\"merchantInfo\":{\"merchantId\":\"200001\"");
        assertThat(responseJson).contains("\"orderId\":\"REQ202607120001\"");
        assertThat(responseJson).contains("\"amount\":12.34");
        assertThat(responseJson).contains("\"sourceTransactionId\":\"source-001\"");
        assertThat(responseJson).contains("\"description\":\"merchant refund note\"");
        assertThat(responseJson).doesNotContain("billingCardHolderInfo");
        assertThat(responseJson).doesNotContain("cardInfo");
    }

    private PaymentServiceImpl newPaymentService(CapturingPaymentInternalClient paymentInternalClient) {
        PaymentClientProperties properties = new PaymentClientProperties();
        properties.setRemoteEnabled(true);
        return new PaymentServiceImpl(
                Mappers.getMapper(OpenApiRequestConverter.class),
                paymentInternalClient,
                properties,
                new OpenApiKeyMaterialFactory(),
                new OpenApiRequestContext(),
                isoDictionaryService(),
                merchantRuntimeProfileCacheService()
        );
    }

    private MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService() {
        MerchantRuntimeProfileCacheService service = mock(MerchantRuntimeProfileCacheService.class);
        MerchantRuntimeProfile profile = new MerchantRuntimeProfile();
        profile.setMerchantId("200001");
        profile.setSettlementCurrency("HKD");
        when(service.findRuntimeProfile("200001")).thenReturn(profile);
        return service;
    }

    private void bindRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Gateway-Client-Ip", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        request.addHeader("Origin", "https://untrusted-header.example");
        request.addHeader("User-Agent", "JUnit");
        OpenApiRequestHeaderDTO headerDTO = new OpenApiRequestHeaderDTO();
        headerDTO.setMerchantId("200001");
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, headerDTO);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ApiMerchantPaymentRequestDTO buildRequest() {
        ApiMerchantPaymentRequestDTO requestDTO = new ApiMerchantPaymentRequestDTO();
        ApiMerchantPaymentRequestDTO.MerchantInfoDTO merchantInfo = new ApiMerchantPaymentRequestDTO.MerchantInfoDTO();
        merchantInfo.setMerchantId("200001");
        ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo = new ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO();
        subMerchantInfo.setSubId("SUB001");
        subMerchantInfo.setSubCompanyName("Demo Merchant");
        subMerchantInfo.setSubStreet("100 Merchant St");
        subMerchantInfo.setSubCity("New York");
        subMerchantInfo.setSubState("NY");
        subMerchantInfo.setSubCountryCode("USA");
        subMerchantInfo.setSubPostal("10002");
        subMerchantInfo.setSubEmail("merchant@example.com");
        subMerchantInfo.setSubPhone("+1-555-0200");
        subMerchantInfo.setSubTaxId("TAX001");
        subMerchantInfo.setMerchantCategory("5311");
        merchantInfo.setSubMerchantInfo(subMerchantInfo);
        requestDTO.setMerchantInfo(merchantInfo);

        ApiMerchantPaymentRequestDTO.OrderInfoDTO orderInfo = new ApiMerchantPaymentRequestDTO.OrderInfoDTO();
        orderInfo.setOrderNo("M202607120001");
        orderInfo.setOrderId("REQ202607120001");
        orderInfo.setAmount(new BigDecimal("12.34"));
        orderInfo.setCurrency("USD");
        requestDTO.setOrderInfo(orderInfo);

        ApiMerchantPaymentRequestDTO.CardInfoDTO cardInfo = new ApiMerchantPaymentRequestDTO.CardInfoDTO();
        cardInfo.setCardNo("5387380678556554");
        cardInfo.setExpirationMonth("03");
        cardInfo.setExpirationYear("2028");
        cardInfo.setSecurityCode("123");
        requestDTO.setCardInfo(cardInfo);

        ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO billing = new ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO();
        billing.setFirstName("John");
        billing.setLastName("Tom");
        billing.setEmail("user@example.com");
        billing.setPhone("+1-555-0100");
        billing.setCountry("USA");
        billing.setCity("New York");
        billing.setStreet("1 Main St");
        billing.setPostal("10001");
        requestDTO.setBillingCardHolderInfo(billing);

        ApiMerchantPaymentRequestDTO.ThreeDsInfoDTO threeDsInfo = new ApiMerchantPaymentRequestDTO.ThreeDsInfoDTO();
        threeDsInfo.setEci("212");
        threeDsInfo.setCavv("abcdefghijklmnopqrstuvwx1234");
        threeDsInfo.setDsTransactionId("b96c957d-daa1-4b7f-b8b4-373fb9dec47b");
        threeDsInfo.setThreeDsVersion("2.2.0");
        requestDTO.setThreeDsInfo(threeDsInfo);

        ApiMerchantPaymentRequestDTO.TransactionInfoDTO transactionInfo = new ApiMerchantPaymentRequestDTO.TransactionInfoDTO();
        transactionInfo.setSourceTransactionId("source-001");
        transactionInfo.setCallbackUrl("https://merchant.example/callback");
        transactionInfo.setMerchantWebsite("https://merchant-shop.example/checkout?cart=ABC123");
        requestDTO.setTransactionInfo(transactionInfo);

        ApiMerchantPaymentRequestDTO.RiskContextDTO riskContext = new ApiMerchantPaymentRequestDTO.RiskContextDTO();
        riskContext.setCustomerId("CUSTOMER-001");
        riskContext.setDeviceFingerprint("DEVICE-FP-001");
        riskContext.setShippingAddress("2 Shipping St");
        riskContext.setShippingPostalCode("10003");
        riskContext.setShippingCountry("USA");
        requestDTO.setRiskContext(riskContext);
        return requestDTO;
    }

    private IsoDictionaryService isoDictionaryService() {
        return new IsoDictionaryService() {
            @Override
            public List<IsoCountryInfo> listCountries() {
                return List.of();
            }

            @Override
            public List<IsoCountryInfo> searchCountries(String keyword) {
                return List.of();
            }

            @Override
            public Optional<IsoCountryInfo> getCountry(String value) {
                return Optional.empty();
            }

            @Override
            public List<IsoCountryInfo> listCountriesByContinent(String continentCode) {
                return List.of();
            }

            @Override
            public List<IsoCountryInfo> listCountriesByCurrency(String currencyAlpha3Code) {
                return List.of();
            }

            @Override
            public List<IsoCurrencyInfo> listCurrencies() {
                return List.of();
            }

            @Override
            public List<IsoCurrencyInfo> searchCurrencies(String keyword) {
                return List.of();
            }

            @Override
            public Optional<IsoCurrencyInfo> getCurrency(String value) {
                return Optional.empty();
            }

            @Override
            public boolean isCurrencyFractionValid(BigDecimal amount, String currencyValue) {
                return true;
            }

            @Override
            public long toMinorUnit(BigDecimal amount, String currencyValue) {
                return amount.movePointRight(2).longValueExact();
            }
        };
    }

    private static class CapturingPaymentInternalClient implements PaymentInternalClient {

        /**
         * request DTO 依赖，用于 Capturing Payment Internal Client 调用对应的数据访问、远程调用或领域服务能力。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private PaymentCreateClientRequestDTO requestDTO;

        /**
         * called Operation，用于保存 Capturing Payment Internal Client 中与 called动作 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private OpenApiPaymentOperationEnum calledOperation;

        /**
         * next Payment Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String nextPaymentStatus = "SUCCESS";

        /**
         * next Authorization Status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；高敏感字段，禁止明文打印日志，禁止写入异常消息。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String nextAuthorizationStatus = "SUCCESS";

        /**
         * next Merchant Response Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String nextMerchantResponseCode = "T202";

        /**
         * next Merchant Response Message，用于保存 Capturing Payment Internal Client 中与 next商户响应说明 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String nextMerchantResponseMessage = "Processing";

        /**
         * next Fail Reason Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String nextFailReasonCode;

        /**
         * next Fail Reason Message，用于保存 Capturing Payment Internal Client 中与 nextfailreason说明 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String nextFailReasonMessage;

        /** 捕获授权内部请求，并生成与授权动作匹配的可配置测试响应。 */
        @Override
        public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.AUTHORIZATION);
        }

        /** 捕获一步支付内部请求，并生成与支付动作匹配的可配置测试响应。 */
        @Override
        public PaymentCreateClientResponseDTO createPayment(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.PAYMENT);
        }

        /** 捕获预授权内部请求，并生成与预授权动作匹配的测试响应。 */
        @Override
        public PaymentCreateClientResponseDTO createPreAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.PRE_AUTHORIZATION);
        }

        /** 捕获增量授权内部请求，并保留原交易关联字段供断言。 */
        @Override
        public PaymentCreateClientResponseDTO createIncrementalAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.INCREMENTAL_AUTHORIZATION);
        }

        /** 捕获请款内部请求，并生成与请款动作匹配的测试响应。 */
        @Override
        public PaymentCreateClientResponseDTO capture(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.CAPTURE);
        }

        /** 捕获预授权完成内部请求，并生成对应测试响应。 */
        @Override
        public PaymentCreateClientResponseDTO preAuthCompletion(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.PRE_AUTH_COMPLETION);
        }

        /** 捕获退款内部请求，并生成与退款动作匹配的测试响应。 */
        @Override
        public PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.REFUND);
        }

        /** 捕获撤销内部请求，并生成与撤销动作匹配的测试响应。 */
        @Override
        public PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.VOID);
        }

        /** 捕获查询条件，并将统一交易响应转换成含单条动作明细的查询结果。 */
        @Override
        public PaymentQueryClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO) {
            PaymentCreateClientResponseDTO createResponseDTO = captureRequest(requestDTO, OpenApiPaymentOperationEnum.QUERY);
            PaymentQueryClientResponseDTO responseDTO = new PaymentQueryClientResponseDTO();
            responseDTO.setMerchantId(createResponseDTO.getMerchantId());
            responseDTO.setMerchantOrderNo(createResponseDTO.getMerchantOrderNo());
            responseDTO.setMerchantOrderId(createResponseDTO.getMerchantOrderId());
            responseDTO.setOrderAmount(createResponseDTO.getOrderAmount());
            responseDTO.setOrderCurrency(createResponseDTO.getOrderCurrency());
            responseDTO.setLabelAmount(createResponseDTO.getLabelAmount());
            responseDTO.setLabelCurrency(createResponseDTO.getLabelCurrency());
            responseDTO.setTransactionAmount(createResponseDTO.getTransactionAmount());
            responseDTO.setTransactionCurrency(createResponseDTO.getTransactionCurrency());
            responseDTO.setTransactionRate(createResponseDTO.getTransactionRate());
            responseDTO.setTransactionTimeZone(createResponseDTO.getTransactionTimeZone());
            PaymentQueryClientResponseDTO.TransactionInfoDTO transactionInfoDTO = new PaymentQueryClientResponseDTO.TransactionInfoDTO();
            transactionInfoDTO.setTransactionId(requestDTO.getTransactionInfo() == null
                    ? createResponseDTO.getTransactionId()
                    : requestDTO.getTransactionInfo().getTransactionId());
            transactionInfoDTO.setSourceTransactionId(createResponseDTO.getSourceTransactionId());
            transactionInfoDTO.setCode(createResponseDTO.getMerchantResponseCode());
            transactionInfoDTO.setMessage(createResponseDTO.getMerchantResponseMessage());
            transactionInfoDTO.setTransactionType(createResponseDTO.getTransactionType());
            transactionInfoDTO.setTransactionDateTime(createResponseDTO.getTransactionDateTime());
            transactionInfoDTO.setRootTransactionDateTime(requestDTO.getTransactionInfo().getRootTransactionDateTime());
            transactionInfoDTO.setPaymentMethod(createResponseDTO.getPaymentMethod());
            transactionInfoDTO.setCardBrand(createResponseDTO.getPaymentBrand());
            transactionInfoDTO.setCardBin(createResponseDTO.getCardBin());
            transactionInfoDTO.setMerchantWebsite("https://merchant-shop.example/checkout?cart=ABC123");
            responseDTO.getTransactionInfo().add(transactionInfoDTO);
            return responseDTO;
        }

        /** 返回固定的渠道回调受理结果，供非回调测试满足客户端契约。 */
        @Override
        public TransactionChannelCallbackClientResponseDTO recordChannelCallback(TransactionChannelCallbackClientRequestDTO requestDTO) {
            TransactionChannelCallbackClientResponseDTO responseDTO = new TransactionChannelCallbackClientResponseDTO();
            responseDTO.setCallbackLogId("CCL202607140001");
            responseDTO.setCallbackId("CCB202607140001");
            responseDTO.setTransactionId(requestDTO.getTransactionId());
            responseDTO.setCallbackStatus("RECEIVED");
            responseDTO.setProcessResult("PENDING_STATE_MAPPING");
            return responseDTO;
        }

        /** 模拟商户响应日志成功命中并完成回写。 */
        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
            return true;
        }

        /** 根据请求过期时间返回固定的新建会话结果。 */
        @Override
        public PaymentCheckoutClientDTOs.SessionCreateResponse createCheckoutSession(
                PaymentCheckoutClientDTOs.SessionCreateRequest requestDTO) {
            PaymentCheckoutClientDTOs.SessionCreateResponse responseDTO = new PaymentCheckoutClientDTOs.SessionCreateResponse();
            responseDTO.setCheckoutSessionId("CS202607140001");
            responseDTO.setCheckoutTokenId("CT202607140001");
            responseDTO.setCheckoutUrl("https://pay.example.com/checkout/token/cover");
            responseDTO.setCheckoutStatus("CREATED");
            responseDTO.setExpireTime(requestDTO.getExpireTime());
            responseDTO.setIdempotentHit(false);
            return responseDTO;
        }

        /** 返回固定可支付会话，用于验证 OpenAPI 会话响应映射。 */
        @Override
        public PaymentCheckoutClientDTOs.SessionQueryResponse queryCheckoutSession(
                PaymentCheckoutClientDTOs.SessionQueryRequest requestDTO) {
            PaymentCheckoutClientDTOs.SessionQueryResponse responseDTO = new PaymentCheckoutClientDTOs.SessionQueryResponse();
            responseDTO.setCheckoutSessionId("CS202607140001");
            responseDTO.setPageState("PAYABLE");
            return responseDTO;
        }

        /** 返回固定处理中结果，用于验证付款尝试提交响应映射。 */
        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse submitCheckoutPayment(
                PaymentCheckoutClientDTOs.PaymentSubmitRequest requestDTO) {
            PaymentCheckoutClientDTOs.PaymentResultResponse responseDTO = new PaymentCheckoutClientDTOs.PaymentResultResponse();
            responseDTO.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
            responseDTO.setCheckoutAttemptId("CA202607140001");
            responseDTO.setPageState("PROCESSING");
            return responseDTO;
        }

        /** 返回固定 PROCESSING 状态，用于验证 OpenAPI 轮询结果映射。 */
        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse queryCheckoutPaymentStatus(
                PaymentCheckoutClientDTOs.PaymentStatusRequest requestDTO) {
            PaymentCheckoutClientDTOs.PaymentResultResponse responseDTO = new PaymentCheckoutClientDTOs.PaymentResultResponse();
            responseDTO.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
            responseDTO.setCheckoutAttemptId(requestDTO.getCheckoutAttemptId());
            responseDTO.setPageState("PROCESSING");
            return responseDTO;
        }

        /** 返回固定 PROCESSING 状态，用于验证 3DS 返回后不会提前宣告支付成功。 */
        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse handleCheckoutThreeDsReturn(
                PaymentCheckoutClientDTOs.ThreeDsReturnRequest requestDTO) {
            PaymentCheckoutClientDTOs.PaymentResultResponse responseDTO = new PaymentCheckoutClientDTOs.PaymentResultResponse();
            responseDTO.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
            responseDTO.setCheckoutAttemptId(requestDTO.getCheckoutAttemptId());
            responseDTO.setPageState("PROCESSING");
            return responseDTO;
        }

        /** 返回固定支持结果，供共享客户端契约编译；普通支付测试不会调用该分支。 */
        @Override
        public PaymentCheckoutClientDTOs.CardBinResponse resolveCheckoutCardBin(
                PaymentCheckoutClientDTOs.CardBinRequest requestDTO) {
            PaymentCheckoutClientDTOs.CardBinResponse responseDTO = new PaymentCheckoutClientDTOs.CardBinResponse();
            responseDTO.setCardBrand("MASTERCARD");
            responseDTO.setRecognized(true);
            responseDTO.setSupported(true);
            return responseDTO;
        }

        private PaymentCreateClientResponseDTO captureRequest(PaymentCreateClientRequestDTO requestDTO,
                                                              OpenApiPaymentOperationEnum operation) {
            this.requestDTO = requestDTO;
            this.calledOperation = operation;
            PaymentCreateClientResponseDTO responseDTO = new PaymentCreateClientResponseDTO();
            responseDTO.setTransactionId("202607120001000001");
            responseDTO.setSourceTransactionId(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getSourceTransactionId());
            responseDTO.setMerchantId(requestDTO.getMerchantId());
            responseDTO.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
            responseDTO.setMerchantOrderId(requestDTO.getMerchantOrderId());
            responseDTO.setSubMerchantInfo(toResponseSubMerchantInfo(requestDTO.getSubMerchantInfo()));
            responseDTO.setStatus("PROCESSING");
            if (OpenApiPaymentOperationEnum.PAYMENT == operation) {
                responseDTO.setStatus(nextPaymentStatus);
                if ("SUCCESS".equals(nextPaymentStatus)) {
                    responseDTO.setTotalAuthorizedAmount(requestDTO.getAmount());
                    responseDTO.setTotalCapturedAmount(requestDTO.getAmount());
                }
            }
            if (OpenApiPaymentOperationEnum.AUTHORIZATION == operation) {
                responseDTO.setStatus(nextAuthorizationStatus);
                if ("SUCCESS".equals(nextAuthorizationStatus)) {
                    responseDTO.setTotalAuthorizedAmount(requestDTO.getAmount());
                }
            }
            responseDTO.setMerchantResponseCode(nextMerchantResponseCode);
            responseDTO.setMerchantResponseMessage(nextMerchantResponseMessage);
            responseDTO.setFailReasonCode(nextFailReasonCode);
            responseDTO.setFailReasonMessage(nextFailReasonMessage);
            responseDTO.setTransactionType(requestDTO.getTransactionType());
            responseDTO.setCurrency(requestDTO.getCurrency());
            responseDTO.setAmount(1234L);
            responseDTO.setOrderAmount(requestDTO.getAmount());
            responseDTO.setOrderCurrency(requestDTO.getCurrency());
            responseDTO.setLabelAmount(requestDTO.getAmount());
            responseDTO.setLabelCurrency(requestDTO.getCurrency());
            responseDTO.setTransactionAmount(requestDTO.getAmount());
            responseDTO.setTransactionCurrency(requestDTO.getCurrency());
            responseDTO.setTransactionRate(new BigDecimal("1.00000000"));
            responseDTO.setTransactionDateTime(requestDTO.getTransactionDateTime());
            responseDTO.setRootTransactionDateTime(requestDTO.getTransactionInfo() == null
                    ? requestDTO.getTransactionDateTime()
                    : requestDTO.getTransactionInfo().getRootTransactionDateTime());
            responseDTO.setTransactionTimeZone("Asia/Shanghai");
            responseDTO.setPaymentMethod(requestDTO.getPaymentMethod());
            responseDTO.setPaymentBrand("MASTERCARD");
            responseDTO.setCardBin("538738****6554");
            responseDTO.setDescription(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getDescription());
            responseDTO.setCallbackUrl(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getCallbackUrl());
            responseDTO.setMerchantWebsite(requestDTO.getTransactionInfo() == null
                    ? null : requestDTO.getTransactionInfo().getMerchantWebsite());
            return responseDTO;
        }

        private PaymentCreateClientResponseDTO.SubMerchantInfoDTO toResponseSubMerchantInfo(PaymentCreateClientRequestDTO.SubMerchantInfoDTO source) {
            if (source == null) {
                return null;
            }
            PaymentCreateClientResponseDTO.SubMerchantInfoDTO target = new PaymentCreateClientResponseDTO.SubMerchantInfoDTO();
            target.setSubId(source.getSubId());
            target.setSubName(source.getSubName());
            target.setSubCompanyName(source.getSubCompanyName());
            target.setSubCountryCode(source.getSubCountryCode());
            target.setSubState(source.getSubState());
            target.setSubCity(source.getSubCity());
            target.setSubStreet(source.getSubStreet());
            target.setSubPostal(source.getSubPostal());
            target.setSubEmail(source.getSubEmail());
            target.setSubPhone(source.getSubPhone());
            target.setSubTaxId(source.getSubTaxId());
            target.setMerchantCategory(source.getMerchantCategory());
            target.setIntesCode(source.getIntesCode());
            target.setChargeType(source.getChargeType());
            return target;
        }
    }
}
