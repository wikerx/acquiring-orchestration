package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
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
import com.scott.payment.openapi.config.HostedCheckoutProperties;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.dto.body.HostedCheckoutBrowserRequestDTOs;
import com.scott.payment.openapi.dto.body.HostedCheckoutSessionCreateRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.service.OpenApiSystemConfigService;
import com.scott.payment.openapi.support.HostedCheckoutTokenSupport;
import com.scott.payment.openapi.support.OpenApiRequestAttributes;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutPaymentResultVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionCreateVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostedCheckoutServiceImplTests {

    private static final String TOKEN_PEPPER = "unit-test-hosted-checkout-token-pepper";
    private static final String RAW_OPAQUE_TOKEN = "raw-browser-token-123";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldCreateHostedCheckoutSessionWithMerchantBindingAndDefaults() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");

        HostedCheckoutSessionCreateVO responseVO =
                checkoutService.createSession("encrypted-request-body", buildCreateRequest("200001"));

        PaymentCheckoutClientDTOs.SessionCreateRequest captured = paymentInternalClient.sessionCreateRequest;
        assertThat(responseVO.getCheckoutInfo().getCheckoutUrl()).isEqualTo("https://pay.example.com/checkout/token/cover");
        assertThat(responseVO.getCheckoutInfo().getExpireTime().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(responseVO.getOrderInfo().getCurrency()).isEqualTo("USD");
        assertThat(captured.getMerchantId()).isEqualTo("200001");
        assertThat(captured.getMerchantOrderNo()).isEqualTo("M202607270001");
        assertThat(captured.getMerchantRequestId()).isEqualTo("REQ202607270001");
        assertThat(captured.getCurrency()).isEqualTo("USD");
        assertThat(captured.getCurrencyExponent()).isEqualTo(2);
        assertThat(captured.getCheckoutDomain()).isEqualTo("https://pay.example.com");
        assertThat(captured.getRetryAllowed()).isEqualTo(1);
        assertThat(captured.getMaxAttemptCount()).isEqualTo(3);
        assertThat(captured.getMerchantDisplayName()).isEqualTo("Demo Sub Merchant");
        assertThat(captured.getAllowedPaymentMethods()).hasSize(1);
        assertThat(captured.getAllowedPaymentMethods().get(0).getPaymentMethod()).isEqualTo("BANK_CARD");
        assertThat(captured.getAllowedPaymentMethods().get(0).getChannelCode()).isEqualTo("MPGS");
        assertThat(captured.getPayerEmailMasked()).isEqualTo("p***@example.com");
        assertThat(captured.getPayerEmailHash()).isNotBlank();
        assertThat(captured.getMerchantNotifyUrlHash()).isNotBlank();
        assertThat(captured.getRequestFingerprint()).isNotBlank();
        assertThat(captured.getRequestSource()).contains("clientIpHash", "originHash");
    }

    @Test
    void shouldUsePlatformConfigWhenMerchantProvidesCheckoutDomain() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");
        HostedCheckoutSessionCreateRequestDTO requestDTO = buildCreateRequest("200001");
        requestDTO.getCheckoutInfo().setCheckoutDomain("https://merchant-controlled.example");

        checkoutService.createSession("encrypted-request-body", requestDTO);

        assertThat(paymentInternalClient.sessionCreateRequest.getCheckoutDomain()).isEqualTo("https://pay.example.com");
    }

    @Test
    void shouldRejectHostedCheckoutSessionWhenMerchantDoesNotMatchJwtContext() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");

        assertThatThrownBy(() -> checkoutService.createSession("encrypted-request-body", buildCreateRequest("200002")))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.MERCHANT_INVALID.getCode());
        assertThat(paymentInternalClient.sessionCreateRequest).isNull();
    }

    @Test
    void shouldHashOpaqueTokenBeforeQueryingCheckoutSession() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");

        HostedCheckoutBrowserRequestDTOs.SessionQueryRequest requestDTO = new HostedCheckoutBrowserRequestDTOs.SessionQueryRequest();
        requestDTO.setOpaqueToken(RAW_OPAQUE_TOKEN);
        requestDTO.setCover("visual-cover");
        requestDTO.setClientContext(clientContext());

        HostedCheckoutSessionVO responseVO = checkoutService.querySession(requestDTO);

        PaymentCheckoutClientDTOs.SessionQueryRequest captured = paymentInternalClient.sessionQueryRequest;
        assertThat(responseVO.getPageState()).isEqualTo("PAYABLE");
        assertThat(captured.getTokenHash()).isEqualTo(HostedCheckoutTokenSupport.hmacSha256Hex(RAW_OPAQUE_TOKEN, TOKEN_PEPPER));
        assertThat(captured.getCover()).isEqualTo("visual-cover");
        assertThat(captured.getDeviceIdHash()).isNotBlank();
        assertThat(captured.getLanguage()).isEqualTo("en-US");
        assertThat(JsonUtils.toJsonString(captured)).doesNotContain(RAW_OPAQUE_TOKEN);
    }

    @Test
    void shouldSubmitCardPaymentWithTokenHashAndMaskedSerializableSummary() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");

        HostedCheckoutPaymentResultVO responseVO = checkoutService.submitPayment(buildSubmitRequest());

        PaymentCheckoutClientDTOs.PaymentSubmitRequest captured = paymentInternalClient.paymentSubmitRequest;
        assertThat(responseVO.getPageState()).isEqualTo("PROCESSING");
        assertThat(captured.getTokenHash()).isEqualTo(HostedCheckoutTokenSupport.hmacSha256Hex(RAW_OPAQUE_TOKEN, TOKEN_PEPPER));
        assertThat(captured.getCardInfo().getCardNo()).isEqualTo("4111111111111111");
        assertThat(captured.getCardInfo().getSecurityCode()).isEqualTo("123");
        assertThat(captured.getBillingCardHolderInfo().getEmail()).isEqualTo("payer@example.com");
        assertThat(captured.getRequestFingerprint()).isNotBlank();
        assertThat(captured.getBrowserInfoJson()).doesNotContain(RAW_OPAQUE_TOKEN, "4111111111111111", "123");
        assertThat(captured.toString()).doesNotContain("4111111111111111", "123", RAW_OPAQUE_TOKEN);

        String maskedJson = SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(captured));
        assertThat(maskedJson).doesNotContain("4111111111111111", "\"securityCode\":\"123\"", RAW_OPAQUE_TOKEN);
        assertThat(maskedJson).contains("\"cardNo\":\"411111******1111\"", "\"securityCode\":\"***\"");
    }

    private HostedCheckoutServiceImpl newCheckoutService(CapturingCheckoutClient paymentInternalClient) {
        HostedCheckoutProperties properties = new HostedCheckoutProperties();
        properties.setTokenPepper(TOKEN_PEPPER);
        properties.setDefaultMaxAttemptCount(3);
        properties.setDefaultExpireMinutes(30);
        properties.setMaxExpireMinutes(120);
        return new HostedCheckoutServiceImpl(
                paymentInternalClient,
                properties,
                new StubSystemConfigService("https://pay.example.com/"),
                new OpenApiRequestContext(),
                new OpenApiKeyMaterialFactory(),
                isoDictionaryService()
        );
    }

    private void bindRequestContext(String merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/rest/checkout/v1/session");
        request.setRemoteAddr("198.51.100.11");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.addHeader("Origin", "https://merchant.example");
        request.addHeader("Referer", "https://merchant.example/order");
        request.addHeader("User-Agent", "JUnit");
        OpenApiRequestHeaderDTO headerDTO = new OpenApiRequestHeaderDTO();
        headerDTO.setMerchantId(merchantId);
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, headerDTO);
        request.setAttribute(OpenApiRequestAttributes.API_VERSION, "v1");
        request.setAttribute(OpenApiRequestAttributes.INTERFACE_TYPE, "checkout");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private HostedCheckoutSessionCreateRequestDTO buildCreateRequest(String merchantId) {
        HostedCheckoutSessionCreateRequestDTO requestDTO = new HostedCheckoutSessionCreateRequestDTO();
        HostedCheckoutSessionCreateRequestDTO.MerchantInfoDTO merchantInfo = new HostedCheckoutSessionCreateRequestDTO.MerchantInfoDTO();
        merchantInfo.setMerchantId(merchantId);
        ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo = new ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO();
        subMerchantInfo.setSubCompanyName("Demo Sub Merchant");
        merchantInfo.setSubMerchantInfo(subMerchantInfo);
        requestDTO.setMerchantInfo(merchantInfo);

        HostedCheckoutSessionCreateRequestDTO.OrderInfoDTO orderInfo = new HostedCheckoutSessionCreateRequestDTO.OrderInfoDTO();
        orderInfo.setOrderNo("M202607270001");
        orderInfo.setOrderId("REQ202607270001");
        orderInfo.setAmount(new BigDecimal("49.97"));
        orderInfo.setCurrency("USD");
        orderInfo.setSubject("Checkout Unit Test");
        orderInfo.setDescription("Hosted Checkout test order");
        HostedCheckoutSessionCreateRequestDTO.OrderItemDTO itemDTO = new HostedCheckoutSessionCreateRequestDTO.OrderItemDTO();
        itemDTO.setName("Test item");
        itemDTO.setQuantity(1);
        itemDTO.setAmount(new BigDecimal("49.97"));
        itemDTO.setCurrency("USD");
        orderInfo.setItems(List.of(itemDTO));
        requestDTO.setOrderInfo(orderInfo);

        HostedCheckoutSessionCreateRequestDTO.CheckoutInfoDTO checkoutInfo = new HostedCheckoutSessionCreateRequestDTO.CheckoutInfoDTO();
        HostedCheckoutSessionCreateRequestDTO.AllowedPaymentMethodDTO methodDTO = new HostedCheckoutSessionCreateRequestDTO.AllowedPaymentMethodDTO();
        methodDTO.setPaymentMethod("bank_card");
        methodDTO.setChannelCode("mpgs");
        methodDTO.setBrands(List.of("VISA", "MASTERCARD"));
        methodDTO.setThreeDsMode("AUTO");
        checkoutInfo.setAllowedPaymentMethods(List.of(methodDTO));
        checkoutInfo.setLocale("en-US");
        checkoutInfo.setReturnUrl("https://merchant.example/return");
        checkoutInfo.setCancelUrl("https://merchant.example/cancel");
        checkoutInfo.setNotifyUrl("https://merchant.example/notify");
        requestDTO.setCheckoutInfo(checkoutInfo);

        HostedCheckoutSessionCreateRequestDTO.PayerInfoDTO payerInfo = new HostedCheckoutSessionCreateRequestDTO.PayerInfoDTO();
        payerInfo.setEmail("payer@example.com");
        payerInfo.setCountry("USA");
        requestDTO.setPayerInfo(payerInfo);
        return requestDTO;
    }

    private HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest buildSubmitRequest() {
        HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO = new HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest();
        requestDTO.setOpaqueToken(RAW_OPAQUE_TOKEN);
        requestDTO.setCheckoutSessionId("CS202607270001");
        requestDTO.setAttemptRequestId("ATT202607270001");
        requestDTO.setPaymentMethod("bank_card");
        requestDTO.setClientContext(clientContext());

        HostedCheckoutBrowserRequestDTOs.CardInfoDTO cardInfo = new HostedCheckoutBrowserRequestDTOs.CardInfoDTO();
        cardInfo.setCardNo("4111111111111111");
        cardInfo.setExpirationMonth("09");
        cardInfo.setExpirationYear("2029");
        cardInfo.setSecurityCode("123");
        cardInfo.setCardholderName("Payer Example");
        requestDTO.setCardInfo(cardInfo);

        HostedCheckoutBrowserRequestDTOs.BillingCardHolderInfoDTO billing = new HostedCheckoutBrowserRequestDTOs.BillingCardHolderInfoDTO();
        billing.setFirstName("Payer");
        billing.setLastName("Example");
        billing.setEmail("payer@example.com");
        billing.setCountry("USA");
        billing.setState("CA");
        billing.setCity("San Francisco");
        billing.setStreet("1 Market St");
        billing.setPostal("94105");
        requestDTO.setBillingCardHolderInfo(billing);
        return requestDTO;
    }

    private HostedCheckoutBrowserRequestDTOs.ClientContextDTO clientContext() {
        HostedCheckoutBrowserRequestDTOs.ClientContextDTO contextDTO = new HostedCheckoutBrowserRequestDTOs.ClientContextDTO();
        contextDTO.setDeviceId("browser-device-id");
        contextDTO.setLanguage("en-US");
        contextDTO.setScreen("1440x900");
        contextDTO.setTimezoneOffset("-480");
        return contextDTO;
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
                if ("USD".equalsIgnoreCase(value)) {
                    return Optional.of(new IsoCurrencyInfo("USD", "840", "US Dollar", "美元", 2, 100L,
                            new BigDecimal("0.01"), "$"));
                }
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

    private static class StubSystemConfigService implements OpenApiSystemConfigService {

        private final String checkoutFrontendBaseUrl;

        private StubSystemConfigService(String checkoutFrontendBaseUrl) {
            this.checkoutFrontendBaseUrl = checkoutFrontendBaseUrl;
        }

        @Override
        public String requiredEnabledValue(String configKey) {
            return checkoutFrontendBaseUrl;
        }
    }

    private static class CapturingCheckoutClient implements PaymentInternalClient {

        private PaymentCheckoutClientDTOs.SessionCreateRequest sessionCreateRequest;
        private PaymentCheckoutClientDTOs.SessionQueryRequest sessionQueryRequest;
        private PaymentCheckoutClientDTOs.PaymentSubmitRequest paymentSubmitRequest;

        @Override
        public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO createPayment(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO createPreAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO createIncrementalAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO capture(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO preAuthCompletion(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentQueryClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransactionChannelCallbackClientResponseDTO recordChannelCallback(
                TransactionChannelCallbackClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCheckoutClientDTOs.SessionCreateResponse createCheckoutSession(
                PaymentCheckoutClientDTOs.SessionCreateRequest requestDTO) {
            this.sessionCreateRequest = requestDTO;
            PaymentCheckoutClientDTOs.SessionCreateResponse responseDTO = new PaymentCheckoutClientDTOs.SessionCreateResponse();
            responseDTO.setCheckoutSessionId("CS202607270001");
            responseDTO.setCheckoutTokenId("CT202607270001");
            responseDTO.setCheckoutUrl("https://pay.example.com/checkout/token/cover");
            responseDTO.setCheckoutStatus("CREATED");
            responseDTO.setExpireTime(LocalDateTime.now().plusMinutes(30));
            responseDTO.setIdempotentHit(false);
            return responseDTO;
        }

        @Override
        public PaymentCheckoutClientDTOs.SessionQueryResponse queryCheckoutSession(
                PaymentCheckoutClientDTOs.SessionQueryRequest requestDTO) {
            this.sessionQueryRequest = requestDTO;
            PaymentCheckoutClientDTOs.SessionQueryResponse responseDTO = new PaymentCheckoutClientDTOs.SessionQueryResponse();
            responseDTO.setCheckoutSessionId("CS202607270001");
            responseDTO.setPageState("PAYABLE");
            PaymentCheckoutClientDTOs.Merchant merchant = new PaymentCheckoutClientDTOs.Merchant();
            merchant.setDisplayName("Demo Sub Merchant");
            responseDTO.setMerchant(merchant);
            PaymentCheckoutClientDTOs.Order order = new PaymentCheckoutClientDTOs.Order();
            order.setOrderNo("M202607270001");
            order.setAmount(new BigDecimal("49.97"));
            order.setCurrency("USD");
            order.setCurrencyExponent(2);
            responseDTO.setOrder(order);
            PaymentCheckoutClientDTOs.Checkout checkout = new PaymentCheckoutClientDTOs.Checkout();
            checkout.setRetryAllowed(true);
            checkout.setRemainingAttemptCount(3);
            checkout.setPollingIntervalSeconds(2);
            responseDTO.setCheckout(checkout);
            return responseDTO;
        }

        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse submitCheckoutPayment(
                PaymentCheckoutClientDTOs.PaymentSubmitRequest requestDTO) {
            this.paymentSubmitRequest = requestDTO;
            PaymentCheckoutClientDTOs.PaymentResultResponse responseDTO = new PaymentCheckoutClientDTOs.PaymentResultResponse();
            responseDTO.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
            responseDTO.setCheckoutAttemptId("CA202607270001");
            responseDTO.setPageState("PROCESSING");
            return responseDTO;
        }

        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse queryCheckoutPaymentStatus(
                PaymentCheckoutClientDTOs.PaymentStatusRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse handleCheckoutThreeDsReturn(
                PaymentCheckoutClientDTOs.ThreeDsReturnRequest requestDTO) {
            throw new UnsupportedOperationException();
        }
    }
}
