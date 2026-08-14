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
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hosted Checkout OpenAPI 服务测试，覆盖商户绑定、Token 不透明性、金额币种和页面状态映射。
 */
class HostedCheckoutServiceImplTests {

    /** 单元测试 Token 摘要盐值，不得复用于运行环境。 */
    private static final String TOKEN_PEPPER = "unit-test-hosted-checkout-token-pepper";

    /** 模拟浏览器持有的不透明原始 Token，用于验证日志与下游请求不泄露明文。 */
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
        assertThat(responseVO.getCheckoutUrl()).isEqualTo("https://pay.example.com/checkout/token/cover");
        assertThat(responseVO.getMerchantInfo().getSubMerchantInfo().getSubCompanyName())
                .isEqualTo("Demo Sub Merchant");
        assertThat(responseVO.getOrderInfo().getCurrency()).isEqualTo("USD");
        assertThat(responseVO.getGoodsInfo()).singleElement()
                .extracting(PaymentCreateVO.GoodsInfoVO::getName)
                .isEqualTo("Test item");
        assertThat(responseVO.getBillingCardHolderInfo().getEmail()).isEqualTo("billing@example.com");
        assertThat(responseVO.getPayerInfo().getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(responseVO.getShippingInfo().getStreet()).isEqualTo("2 Shipping St");
        assertThat(responseVO.getTransactionInfo().getRedirectUrl())
                .isEqualTo("https://merchant.example/result");
        assertThat(captured.getMerchantId()).isEqualTo("200001");
        assertThat(captured.getMerchantOrderNo()).isEqualTo("M202607270001");
        assertThat(captured.getMerchantRequestId()).isEqualTo("REQ202607270001");
        assertThat(captured.getCurrency()).isEqualTo("USD");
        assertThat(captured.getCurrencyExponent()).isEqualTo(2);
        assertThat(captured.getCheckoutDomain()).isEqualTo("https://pay.example.com");
        assertThat(captured.getRetryAllowed()).isEqualTo(1);
        assertThat(captured.getMaxAttemptCount()).isEqualTo(3);
        assertThat(captured.getMerchantDisplayName()).isEqualTo("Demo Sub Merchant");
        assertThat(captured.getAllowedPaymentMethods()).isEmpty();
        assertThat(captured.getSubMerchantInfoJson()).contains("Demo Sub Merchant");
        assertThat(captured.getPayerInfoJson()).contains("203.0.113.9");
        assertThat(captured.getBillingInfoJson()).contains("billing@example.com");
        assertThat(captured.getShippingInfoJson()).contains("2 Shipping St", "shipping@example.com");
        assertThat(captured.getPayerEmail()).isEqualTo("payer@example.com");
        assertThat(captured.getPayerEmailHash()).isNotBlank();
        assertThat(captured.getMerchantNotifyUrl()).isEqualTo("https://merchant.example/notify");
        assertThat(captured.getRedirectUrl()).isEqualTo("https://merchant.example/result");
        assertThat(captured.getRequestFingerprint()).isNotBlank();
        assertThat(captured.getRequestSource()).contains("clientIpHash", "originHash");
    }

    @Test
    void shouldLeavePaymentMethodSelectionToPlatformConfiguration() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");

        checkoutService.createSession("encrypted-request-body", buildCreateRequest("200001"));

        assertThat(paymentInternalClient.sessionCreateRequest.getAllowedPaymentMethods()).isEmpty();
        assertThat(paymentInternalClient.sessionCreateRequest.getCheckoutDomain()).isEqualTo("https://pay.example.com");
        assertThat(paymentInternalClient.sessionCreateRequest.getRetryAllowed()).isEqualTo(1);
        assertThat(paymentInternalClient.sessionCreateRequest.getMaxAttemptCount()).isEqualTo(3);
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
    void shouldRejectInvalidMerchantPayerIpBeforeCreatingSession() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");
        HostedCheckoutSessionCreateRequestDTO requestDTO = buildCreateRequest("200001");
        requestDTO.getPayerInfo().setIpAddress("203.0.113.9, 10.0.0.1");

        assertThatThrownBy(() -> checkoutService.createSession("encrypted-request-body", requestDTO))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
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
        assertThat(responseVO.getCardEncryption()).isNotNull();
        assertThat(responseVO.getCardEncryption().getAlgorithm()).isEqualTo("RSA-OAEP-256+A256GCM");
        assertThat(responseVO.getCardEncryption().getKeyId()).isEqualTo("checkout-card-v1");
        assertThat(responseVO.getCardEncryption().getPublicKey()).isEqualTo("public-key-base64");
        assertThat(responseVO.getCardEncryption().getNonce()).isEqualTo("checkout-nonce");
        assertThat(JsonUtils.toJsonString(captured)).doesNotContain(RAW_OPAQUE_TOKEN);
    }

    @Test
    void shouldSubmitEncryptedCardEnvelopeWithoutPlainCardData() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");

        HostedCheckoutPaymentResultVO responseVO = checkoutService.submitPayment(buildSubmitRequest());

        PaymentCheckoutClientDTOs.PaymentSubmitRequest captured = paymentInternalClient.paymentSubmitRequest;
        assertThat(responseVO.getPageState()).isEqualTo("PROCESSING");
        assertThat(captured.getTokenHash()).isEqualTo(HostedCheckoutTokenSupport.hmacSha256Hex(RAW_OPAQUE_TOKEN, TOKEN_PEPPER));
        assertThat(captured.getCardDataEnvelope().getAlgorithm()).isEqualTo("RSA-OAEP-256+A256GCM");
        assertThat(captured.getCardDataEnvelope().getKeyId()).isEqualTo("checkout-card-v1");
        assertThat(captured.getCardDataEnvelope().getEncryptedKey()).startsWith("encryptedKey");
        assertThat(captured.getBillingCardHolderInfo().getEmail()).isEqualTo("payer@example.com");
        assertThat(captured.getPayerIp()).isEqualTo("203.0.113.9");
        assertThat(captured.getRequestFingerprint()).isNotBlank();
        assertThat(captured.getBrowserInfoJson()).doesNotContain(RAW_OPAQUE_TOKEN);
        assertThat(captured.getBrowserInfoJson())
                .contains("\"userAgent\":\"JUnit\"")
                .contains("\"acceptHeaders\":\"text/html,application/xhtml+xml\"")
                .contains("\"challengeWindowSize\":\"FULL_SCREEN\"")
                .contains("\"colorDepth\":24")
                .contains("\"javaScriptEnabled\":true")
                .contains("\"screenHeight\":900")
                .contains("\"screenWidth\":1440");
        assertThat(captured.toString()).doesNotContain("cardNo", "securityCode", RAW_OPAQUE_TOKEN);

        String maskedJson = SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(captured));
        assertThat(maskedJson).doesNotContain("encryptedKeyValue", "ciphertextValue", RAW_OPAQUE_TOKEN);
        assertThat(maskedJson).contains("\"encryptedKey\":\"***\"", "\"ciphertext\":\"***\"");
    }

    @Test
    void shouldRejectMalformedPayerIpBeforeCallingPaymentService() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        String malformedIp = "203.0.113.9 injected-value";
        bindRequestContext("200001", malformedIp);

        assertThatThrownBy(() -> checkoutService.submitPayment(buildSubmitRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("payer IP address is invalid")
                .hasMessageNotContaining(malformedIp);
        assertThat(paymentInternalClient.paymentSubmitRequest).isNull();
    }

    @Test
    void shouldForwardThreeDsReturnSafelyAndExposeFreshCardEncryptionMetadata() {
        CapturingCheckoutClient paymentInternalClient = new CapturingCheckoutClient();
        HostedCheckoutServiceImpl checkoutService = newCheckoutService(paymentInternalClient);
        bindRequestContext("200001");
        HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest requestDTO = buildThreeDsReturnRequest();

        HostedCheckoutPaymentResultVO responseVO = checkoutService.handleThreeDsReturn(requestDTO);

        PaymentCheckoutClientDTOs.ThreeDsReturnRequest captured = paymentInternalClient.threeDsReturnRequest;
        assertThat(responseVO.getPageState()).isEqualTo("THREE_DS_REQUIRED");
        assertThat(responseVO.getThreeDsAction().getPhase()).isEqualTo("AUTHENTICATE");
        assertThat(responseVO.getThreeDsAction().getCardEncryption().getAlgorithm())
                .isEqualTo("RSA-OAEP-256+A256GCM");
        assertThat(responseVO.getThreeDsAction().getCardEncryption().getNonce()).isEqualTo("fresh-3ds-nonce");
        assertThat(captured.getThreeDsReturnTokenHash())
                .isEqualTo(HostedCheckoutTokenSupport.hmacSha256Hex("raw-3ds-return-token", TOKEN_PEPPER));
        assertThat(captured.getAuthenticationDataJsonMasked()).contains("\"cres\":\"***\"", "\"cavv\":\"***\"");
        assertThat(captured.getAuthenticationDataJsonMasked()).doesNotContain("raw-cres-value", "raw-cavv-value");
        assertThat(captured.getCardDataEnvelope().getEncryptedKey()).startsWith("encryptedKey");
        assertThat(captured.getBillingCardHolderInfo().getEmail()).isEqualTo("payer@example.com");
        assertThat(captured.getPayerIp()).isEqualTo("203.0.113.9");
        assertThat(captured.getBrowserInfoJson()).contains("\"language\":\"en-US\"");
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
        bindRequestContext(merchantId, "203.0.113.9, 10.0.0.1");
    }

    private void bindRequestContext(String merchantId, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/rest/checkout/v1/session");
        request.setRemoteAddr("198.51.100.11");
        request.addHeader("X-Forwarded-For", forwardedFor);
        request.addHeader("Origin", "https://merchant.example");
        request.addHeader("Referer", "https://merchant.example/order");
        request.addHeader("User-Agent", "JUnit");
        request.addHeader("Accept", "text/html,application/xhtml+xml");
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
        requestDTO.setOrderInfo(orderInfo);

        ApiMerchantPaymentRequestDTO.GoodsInfoDTO itemDTO = new ApiMerchantPaymentRequestDTO.GoodsInfoDTO();
        itemDTO.setName("Test item");
        itemDTO.setQuantity(1);
        itemDTO.setAmount(new BigDecimal("49.97"));
        itemDTO.setCurrency("USD");
        requestDTO.setGoodsInfo(List.of(itemDTO));

        ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO billingInfo =
                new ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO();
        billingInfo.setFirstName("Billing");
        billingInfo.setLastName("Example");
        billingInfo.setEmail("billing@example.com");
        billingInfo.setCountry("USA");
        billingInfo.setState("CA");
        billingInfo.setCity("San Francisco");
        billingInfo.setStreet("1 Billing St");
        billingInfo.setPostal("94105");
        requestDTO.setBillingCardHolderInfo(billingInfo);

        ApiMerchantPaymentRequestDTO.PayerInfoDTO payerInfo = new ApiMerchantPaymentRequestDTO.PayerInfoDTO();
        payerInfo.setFirstName("Payer");
        payerInfo.setLastName("Example");
        payerInfo.setEmail("payer@example.com");
        payerInfo.setCountry("USA");
        payerInfo.setIpAddress("203.0.113.9");
        payerInfo.setSessionId("SESSION-001");
        payerInfo.setBrowserInfo(Map.of("browser", Map.of("name", "Chrome", "version", "128")));
        payerInfo.setUserAgent("JUnit Merchant Client");
        requestDTO.setPayerInfo(payerInfo);

        ApiMerchantPaymentRequestDTO.ShippingInfoDTO shippingInfo =
                new ApiMerchantPaymentRequestDTO.ShippingInfoDTO();
        shippingInfo.setFirstName("Shipping");
        shippingInfo.setLastName("Example");
        shippingInfo.setEmail("shipping@example.com");
        shippingInfo.setCountry("USA");
        shippingInfo.setState("CA");
        shippingInfo.setCity("San Francisco");
        shippingInfo.setStreet("2 Shipping St");
        shippingInfo.setPostal("94105");
        requestDTO.setShippingInfo(shippingInfo);

        HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO transactionInfo =
                new HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO();
        transactionInfo.setDescription("Hosted Checkout test order");
        transactionInfo.setCallbackUrl("https://merchant.example/notify");
        transactionInfo.setRedirectUrl("https://merchant.example/result");
        transactionInfo.setLanguage("en-US");
        requestDTO.setTransactionInfo(transactionInfo);
        return requestDTO;
    }

    private HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest buildSubmitRequest() {
        HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO = new HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest();
        requestDTO.setOpaqueToken(RAW_OPAQUE_TOKEN);
        requestDTO.setCheckoutSessionId("CS202607270001");
        requestDTO.setAttemptRequestId("ATT202607270001");
        requestDTO.setPaymentMethod("bank_card");
        requestDTO.setClientContext(clientContext());

        HostedCheckoutBrowserRequestDTOs.CardDataEnvelopeDTO envelope =
                new HostedCheckoutBrowserRequestDTOs.CardDataEnvelopeDTO();
        envelope.setAlgorithm("RSA-OAEP-256+A256GCM");
        envelope.setKeyId("checkout-card-v1");
        envelope.setEncryptedKey("encryptedKeyValue" + "A".repeat(64));
        envelope.setIv("ivValue1234567890");
        envelope.setCiphertext("ciphertextValue1234567890");
        envelope.setNonce("nonceValue1234567890");
        requestDTO.setCardDataEnvelope(envelope);

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

    private HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest buildThreeDsReturnRequest() {
        HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest submitRequest = buildSubmitRequest();
        HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest requestDTO =
                new HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest();
        requestDTO.setThreeDsReturnToken("raw-3ds-return-token");
        requestDTO.setCheckoutSessionId(submitRequest.getCheckoutSessionId());
        requestDTO.setCheckoutAttemptId("CA202607270001");
        requestDTO.setAuthenticationData("{\"cres\":\"raw-cres-value\",\"cavv\":\"raw-cavv-value\"}");
        requestDTO.setCardDataEnvelope(submitRequest.getCardDataEnvelope());
        requestDTO.setBillingCardHolderInfo(submitRequest.getBillingCardHolderInfo());
        requestDTO.setClientContext(submitRequest.getClientContext());
        return requestDTO;
    }

    private HostedCheckoutBrowserRequestDTOs.ClientContextDTO clientContext() {
        HostedCheckoutBrowserRequestDTOs.ClientContextDTO contextDTO = new HostedCheckoutBrowserRequestDTOs.ClientContextDTO();
        contextDTO.setDeviceId("browser-device-id");
        contextDTO.setLanguage("en-US");
        contextDTO.setScreen("1440x900");
        contextDTO.setTimezoneOffset("-480");
        contextDTO.setChallengeWindowSize("FULL_SCREEN");
        contextDTO.setColorDepth(24);
        contextDTO.setJavaEnabled(false);
        contextDTO.setJavaScriptEnabled(true);
        contextDTO.setScreenHeight(900);
        contextDTO.setScreenWidth(1440);
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

        /** 测试用收银台前端根地址。 */
        private final String checkoutFrontendBaseUrl;

        private StubSystemConfigService(String checkoutFrontendBaseUrl) {
            this.checkoutFrontendBaseUrl = checkoutFrontendBaseUrl;
        }

        /** 对所有受测配置键返回固定前端地址，使测试不依赖数据库或配置中心。 */
        @Override
        public String requiredEnabledValue(String configKey) {
            return checkoutFrontendBaseUrl;
        }
    }

    private static class CapturingCheckoutClient implements PaymentInternalClient {

        /** 捕获的会话创建请求，用于断言商户、订单、金额和回调配置。 */
        private PaymentCheckoutClientDTOs.SessionCreateRequest sessionCreateRequest;

        /** 捕获的会话查询请求，用于断言不透明 Token 的下游传递方式。 */
        private PaymentCheckoutClientDTOs.SessionQueryRequest sessionQueryRequest;

        /** 捕获的支付提交请求，用于断言卡数据和 3DS 参数的最小传递范围。 */
        private PaymentCheckoutClientDTOs.PaymentSubmitRequest paymentSubmitRequest;

        /** 捕获的 3DS 回跳请求，用于断言令牌摘要、敏感载荷脱敏和卡密文续传。 */
        private PaymentCheckoutClientDTOs.ThreeDsReturnRequest threeDsReturnRequest;

        /** 本测试桩不覆盖授权交易，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖普通支付交易，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentCreateClientResponseDTO createPayment(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖预授权交易，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentCreateClientResponseDTO createPreAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖增量授权，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentCreateClientResponseDTO createIncrementalAuthorization(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖请款，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentCreateClientResponseDTO capture(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖预授权完成，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentCreateClientResponseDTO preAuthCompletion(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖退款，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖撤销，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖交易查询，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public PaymentQueryClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖渠道回调落库，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public TransactionChannelCallbackClientResponseDTO recordChannelCallback(
                TransactionChannelCallbackClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 本测试桩不覆盖商户响应日志更新，调用即表示 Hosted Checkout 测试路径越界。 */
        @Override
        public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 捕获会话创建请求并返回确定性 CREATED 响应。 */
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

        /** 捕获会话查询请求并返回可支付页面所需的最小商户和订单视图。 */
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
            PaymentCheckoutClientDTOs.CardEncryption cardEncryption =
                    new PaymentCheckoutClientDTOs.CardEncryption();
            cardEncryption.setAlgorithm("RSA-OAEP-256+A256GCM");
            cardEncryption.setKeyId("checkout-card-v1");
            cardEncryption.setPublicKey("public-key-base64");
            cardEncryption.setNonce("checkout-nonce");
            responseDTO.setCardEncryption(cardEncryption);
            return responseDTO;
        }

        /** 捕获支付提交请求并返回 PROCESSING，防止测试把受理误判为成功。 */
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

        /** 当前测试不覆盖轮询分支，调用即表示用例准备不完整。 */
        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse queryCheckoutPaymentStatus(
                PaymentCheckoutClientDTOs.PaymentStatusRequest requestDTO) {
            throw new UnsupportedOperationException();
        }

        /** 捕获 3DS 回跳请求并返回下一阶段动作，验证新 nonce 能透传到浏览器。 */
        @Override
        public PaymentCheckoutClientDTOs.PaymentResultResponse handleCheckoutThreeDsReturn(
                PaymentCheckoutClientDTOs.ThreeDsReturnRequest requestDTO) {
            this.threeDsReturnRequest = requestDTO;
            PaymentCheckoutClientDTOs.PaymentResultResponse responseDTO = new PaymentCheckoutClientDTOs.PaymentResultResponse();
            responseDTO.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
            responseDTO.setCheckoutAttemptId(requestDTO.getCheckoutAttemptId());
            responseDTO.setPageState("THREE_DS_REQUIRED");
            PaymentCheckoutClientDTOs.ThreeDsAction action = new PaymentCheckoutClientDTOs.ThreeDsAction();
            action.setActionType("HTML_CHALLENGE");
            action.setPhase("AUTHENTICATE");
            action.setHtml("<form action=\"https://acs.example.test/challenge\"></form>");
            action.setReturnUrl("https://api.example.test/checkout/api/v1/3ds/bridge");
            action.setTimeoutSeconds(300);
            PaymentCheckoutClientDTOs.CardEncryption cardEncryption = new PaymentCheckoutClientDTOs.CardEncryption();
            cardEncryption.setAlgorithm("RSA-OAEP-256+A256GCM");
            cardEncryption.setKeyId("checkout-card-v1");
            cardEncryption.setPublicKey("public-key-base64");
            cardEncryption.setNonce("fresh-3ds-nonce");
            action.setCardEncryption(cardEncryption);
            responseDTO.setThreeDsAction(action);
            return responseDTO;
        }

        /** 当前测试不覆盖卡 BIN 解析，调用即表示用例准备不完整。 */
        @Override
        public PaymentCheckoutClientDTOs.CardBinResponse resolveCheckoutCardBin(
                PaymentCheckoutClientDTOs.CardBinRequest requestDTO) {
            throw new UnsupportedOperationException();
        }
    }
}
