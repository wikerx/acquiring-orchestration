package com.scott.payment.openapi.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
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
        assertThat(captured.getTransactionInfo().getCardBrand()).isEqualTo("MASTERCARD");
        assertThat(captured.getCallbackUrl()).isEqualTo("https://merchant.example/callback");
        assertThat(captured.getSourceUrl()).isEqualTo("https://checkout.example");
        assertThat(captured.getPayerIp()).isEqualTo("203.0.113.1");
        assertThat(captured.getUserAgent()).isEqualTo("JUnit");
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
        requestDTO.getTransactionInfo().setTransactionId("202607120001000001");
        bindRequestContext();

        PaymentQueryVO responseVO = paymentService.queryTransaction("encrypted-body", requestDTO);

        assertThat(paymentInternalClient.calledOperation).isEqualTo(OpenApiPaymentOperationEnum.QUERY);
        assertThat(paymentInternalClient.requestDTO.getTransactionType()).isEqualTo("QUERY");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderId()).isEqualTo("REQ202607120001");
        assertThat(paymentInternalClient.requestDTO.getRequestId()).isEqualTo("REQ202607120001");
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionId()).isNull();
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getTransactionId()).isEqualTo("202607120001000001");
        assertThat(responseVO.getTransactionInfo()).hasSize(1);
        assertThat(responseVO.getTransactionInfo().get(0).getTransactionId()).isEqualTo("202607120001000001");
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
        requestDTO.getTransactionInfo().setDescription("merchant refund note");
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.refund("encrypted-body", requestDTO));

        assertThat(paymentInternalClient.calledOperation).isEqualTo(OpenApiPaymentOperationEnum.REFUND);
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderNo()).isNull();
        assertThat(paymentInternalClient.requestDTO.getCurrency()).isNull();
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionId()).isEqualTo("source-001");
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
                baseMerchantInfoMapper()
        );
    }

    private BaseMerchantInfoMapper baseMerchantInfoMapper() {
        BaseMerchantInfoMapper mapper = mock(BaseMerchantInfoMapper.class);
        BaseMerchantInfoDO merchantInfoDO = new BaseMerchantInfoDO();
        merchantInfoDO.setMerchantId("200001");
        merchantInfoDO.setSettlementCurrency("HKD");
        when(mapper.selectOne(any())).thenReturn(merchantInfoDO);
        return mapper;
    }

    private void bindRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        request.addHeader("Origin", "https://checkout.example");
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
        transactionInfo.setCardBrand("MASTERCARD");
        requestDTO.setTransactionInfo(transactionInfo);
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
         * request DTO 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private PaymentCreateClientRequestDTO requestDTO;

        /**
         * called Operation 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private OpenApiPaymentOperationEnum calledOperation;

        /**
         * next Payment Status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String nextPaymentStatus = "SUCCESS";

        /**
         * next Authorization Status 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；高敏感字段，禁止打印日志、禁止写入异常消息，持久化前需确认安全要求。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String nextAuthorizationStatus = "SUCCESS";

        /**
         * next Merchant Response Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String nextMerchantResponseCode = "T202";

        /**
         * next Merchant Response Message 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String nextMerchantResponseMessage = "Processing";

        /**
         * next Fail Reason Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String nextFailReasonCode;

        /**
         * next Fail Reason Message 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private String nextFailReasonMessage;

        @Override
        public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.AUTHORIZATION);
        }

        @Override
        public PaymentCreateClientResponseDTO createPayment(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.PAYMENT);
        }

        @Override
        public PaymentCreateClientResponseDTO createPreAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.PRE_AUTHORIZATION);
        }

        @Override
        public PaymentCreateClientResponseDTO createIncrementalAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.INCREMENTAL_AUTHORIZATION);
        }

        @Override
        public PaymentCreateClientResponseDTO capture(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.CAPTURE);
        }

        @Override
        public PaymentCreateClientResponseDTO preAuthCompletion(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.PRE_AUTH_COMPLETION);
        }

        @Override
        public PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.REFUND);
        }

        @Override
        public PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.VOID);
        }

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
            transactionInfoDTO.setPaymentMethod(createResponseDTO.getPaymentMethod());
            transactionInfoDTO.setCardBrand(createResponseDTO.getPaymentBrand());
            transactionInfoDTO.setCardBin(createResponseDTO.getCardBin());
            responseDTO.getTransactionInfo().add(transactionInfoDTO);
            return responseDTO;
        }

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

        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
            return true;
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
            responseDTO.setTransactionTimeZone("Asia/Shanghai");
            responseDTO.setPaymentMethod(requestDTO.getPaymentMethod());
            responseDTO.setPaymentBrand("MASTERCARD");
            responseDTO.setCardBin("538738****6554");
            responseDTO.setDescription(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getDescription());
            responseDTO.setCallbackUrl(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getCallbackUrl());
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
