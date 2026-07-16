package com.scott.payment.openapi.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
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
    void shouldPassQueryOperationWithMerchantAndSourceTransactionIdsToPaymentService() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        ApiMerchantPaymentRequestDTO requestDTO = buildRequest();
        bindRequestContext();

        paymentService.queryTransaction("encrypted-body", requestDTO);

        assertThat(paymentInternalClient.calledOperation).isEqualTo(OpenApiPaymentOperationEnum.QUERY);
        assertThat(paymentInternalClient.requestDTO.getTransactionType()).isEqualTo("QUERY");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(paymentInternalClient.requestDTO.getMerchantOrderId()).isEqualTo("REQ202607120001");
        assertThat(paymentInternalClient.requestDTO.getRequestId()).isEqualTo("REQ202607120001");
        assertThat(paymentInternalClient.requestDTO.getTransactionInfo().getSourceTransactionId()).isEqualTo("source-001");
    }

    @Test
    void shouldSerializeMerchantResponseWithoutNullAndDccEdcFlags() {
        CapturingPaymentInternalClient paymentInternalClient = new CapturingPaymentInternalClient();
        PaymentServiceImpl paymentService = newPaymentService(paymentInternalClient);
        bindRequestContext();

        String responseJson = JsonUtils.toJsonString(paymentService.createAuthorization("encrypted-body", buildRequest()));
        Map<String, Object> responseMap = JsonUtils.parseObject(responseJson, new TypeReference<>() {
        });

        assertThat(responseJson).contains("\"merchantInfo\"");
        assertThat(responseJson).contains("\"transactionInfo\"");
        assertThat(responseJson).contains("\"billingInfo\"");
        assertThat(responseJson).contains("\"transactionRate\":1.00000000");
        assertThat(responseJson).contains("\"currency\":\"USD\"");
        assertThat(responseJson).contains("\"transactionStatus\":\"PROCESSING\"");
        assertThat(responseJson).doesNotContain("\"status\":\"PROCESSING\"");
        assertThat(responseJson).doesNotContain("operationId");
        assertThat(responseJson).doesNotContain("channelTransactionId");
        assertThat(responseJson).doesNotContain("dccEnabled");
        assertThat(responseJson).doesNotContain("edcEnabled");
        assertThat(responseJson).doesNotContain("null");
        assertThat(responseMap).containsOnlyKeys("merchantInfo", "orderInfo", "transactionInfo", "billingInfo");
        assertThat(responseMap).doesNotContainKeys("merchantId", "orderNo", "orderId", "transactionId", "status", "currency", "amount");
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
                isoDictionaryService()
        );
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
        ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo = new ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO();
        subMerchantInfo.setSubId("SUB001");
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

        private PaymentCreateClientRequestDTO requestDTO;

        private OpenApiPaymentOperationEnum calledOperation;

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
        public PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.REFUND);
        }

        @Override
        public PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.VOID);
        }

        @Override
        public PaymentCreateClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO) {
            return captureRequest(requestDTO, OpenApiPaymentOperationEnum.QUERY);
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
            responseDTO.setMerchantId(requestDTO.getMerchantId());
            responseDTO.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
            responseDTO.setMerchantOrderId(requestDTO.getMerchantOrderId());
            responseDTO.setStatus("PROCESSING");
            responseDTO.setMerchantResponseCode("T202");
            responseDTO.setMerchantResponseMessage("Processing");
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
            responseDTO.setPaymentBrand(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getCardBrand());
            responseDTO.setCardBin("538738****6554");
            return responseDTO;
        }
    }
}
