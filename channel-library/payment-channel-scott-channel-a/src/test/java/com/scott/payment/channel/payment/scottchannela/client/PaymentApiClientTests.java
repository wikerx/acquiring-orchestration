package com.scott.payment.channel.payment.scottchannela.client;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.scottchannela.config.ChannelProperties;
import com.scott.payment.component.core.json.JsonUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScottChannelA 请求映射、响应语义、未知结果和敏感信息脱敏测试。
 **/
class PaymentApiClientTests {

    private HttpServer server;
    private AtomicReference<String> requestPath;
    private AtomicReference<String> requestBody;
    private AtomicReference<String> responseBody;
    private AtomicReference<Integer> responseStatus;

    @BeforeEach
    void setUp() throws IOException {
        requestPath = new AtomicReference<>();
        requestBody = new AtomicReference<>();
        responseStatus = new AtomicReference<>(200);
        responseBody = new AtomicReference<>("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"ORDER-001\",\"transactionId\":\"TX-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"currency\":\"USD\",\"amount\":10.25}}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldUseConfiguredVersionAndDirectPayinPath() {
        ChannelProperties properties = properties();
        PaymentApiClient client = new PaymentApiClient(properties);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("PAYMENT");
        request.setTransactionId("CMT-001");
        request.setMerchantOrderNo("ORDER-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("SUCCESS");
        assertThat(requestPath).hasValue("/api/v7/payins/direct");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("merchantOrderNo", "CMT-001");
        assertThat(payload).containsEntry("currency", "USD");
        assertThat(payload).containsEntry("paymentMethod", "CARD");
        assertThat(payload).containsKey("requestTime");
    }

    @Test
    void shouldUseChannelRequestUrlInsteadOfMidApiBaseUrl() {
        ChannelProperties properties = properties();
        PaymentApiClient client = new PaymentApiClient(properties);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("PAYMENT");
        request.setMerchantOrderNo("ORDER-CHANNEL-URL");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");
        request.getExtension().put("requestUrl", properties.getBaseUrl());
        request.getExtension().put("mid.apiBaseUrl", "http://127.0.0.1:1");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/direct");
    }

    @Test
    void shouldSendOnlyMerchantOrderNoForPayinQueryWhenTransactionIdIsAbsent() {
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.setMerchantOrderNo("ORDER-QUERY-001");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/query");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("merchantOrderNo", "ORDER-QUERY-001");
        assertThat(payload).doesNotContainKey("transactionId");
    }

    @Test
    void shouldUseOriginalPaymentTransactionIdForRefund() {
        responseStatus.set(201);
        responseBody.set(refundResponse());
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("CMT-REFUND-ACTION");
        request.setSourceTransactionId("CMT-ORIGINAL-PAYMENT");
        request.setChannelOrderNo("CMT-ORIGINAL-PAYMENT");
        request.setChannelTransactionId("CH-REFUND-ACTION");
        request.setMerchantOrderNo("SDKPAY-001");
        request.setMerchantOrderId("REFUND-REQUEST-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.getExtension().put("merchantRefundNo", "REFUND-001");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");
        request.getExtension().put("simulationResult", "SUCCESS");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/refunds");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("transactionId", "CMT-ORIGINAL-PAYMENT");
        assertThat(payload).doesNotContainKey("merchantOrderNo");
        assertThat(payload).doesNotContainEntry("transactionId", "CH-REFUND-ACTION");
        assertThat(payload).doesNotContainEntry("transactionId", "CMT-REFUND-ACTION");
        assertThat(payload)
                .containsEntry("merchantRefundNo", "REFUND-001")
                .containsEntry("simulationResult", "SUCCESS");
    }

    /**
     * 数据库存储的六位小数金额必须按渠道两位小数协议发送。
     **/
    @Test
    void shouldNormalizeRefundAmountToTwoDecimalPlaces() {
        responseStatus.set(201);
        responseBody.set(refundResponse());
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("REFUND-ACTION-SCALE-001");
        request.setMerchantOrderId("REFUND-SCALE-001");
        request.setAmount(new java.math.BigDecimal("8.850000"));
        request.setCurrency("HKD");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");

        client.execute(request);

        assertThat(requestBody.get()).contains("\"amount\":8.85");
        assertThat(requestBody.get()).doesNotContain("\"amount\":8.850000");
    }

    /**
     * 渠道明确返回 4xx 时请求已被拒绝，不能继续作为结果未知订单处理。
     **/
    @Test
    void shouldMapClientErrorToDeterministicFailure() {
        responseStatus.set(400);
        responseBody.set("{\"code\":\"INVALID_REQUEST\",\"message\":\"Request validation failed\",\"errors\":[\"amount: scale must be less than or equal to 2\"]}");
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("REFUND-ACTION-INVALID-001");
        request.setMerchantOrderId("REFUND-INVALID-001");
        request.setAmount(new java.math.BigDecimal("8.85"));
        request.setCurrency("HKD");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("FAILED");
        assertThat(response.getRawChannelStatus()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getChannelResponseCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getChannelResponseMessage()).isEqualTo("Request validation failed");
        assertThat(response.getHttpStatus()).isEqualTo(400);
        assertThat(response.getResponseBodyJsonMasked()).contains("INVALID_REQUEST");
    }

    /**
     * 5xx 无法证明资金动作是否被渠道受理，仍需保持结果未知并后续勾兑。
     **/
    @Test
    void shouldKeepServerErrorOutcomeUncertain() {
        responseStatus.set(500);
        responseBody.set("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}");
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("REFUND");
        request.setTransactionId("REFUND-ACTION-SERVER-ERROR-001");
        request.setMerchantOrderId("REFUND-SERVER-ERROR-001");
        request.setAmount(new java.math.BigDecimal("8.85"));
        request.setCurrency("HKD");
        request.getExtension().put("targetTransactionId", "CMT-ORIGINAL-PAYMENT");

        assertThatThrownBy(() -> client.execute(request))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /**
     * 退款查询必须调用退款查询接口，并使用 merchantRefundNo 作为唯一查询标识。
     **/
    @Test
    void shouldQueryRefundByMerchantRefundNoAndMapRefundId() {
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"CMT-ORIGINAL-PAYMENT\",\"transactionId\":\"CMT-ORIGINAL-PAYMENT\",\"merchantRefundNo\":\"REFUND-001\",\"refundId\":\"CMR-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"currency\":\"USD\",\"amount\":10.25}}");
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.setChannelOrderNo("CMT-ORIGINAL-PAYMENT");
        request.setChannelTransactionId("CH-REFUND-ACTION");
        request.setMerchantOrderNo("SDKPAY-001");
        request.getExtension().put("merchantRefundNo", "REFUND-001");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/refunds/query");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("merchantRefundNo", "REFUND-001");
        assertThat(payload).doesNotContainKey("refundId");
        assertThat(response.getChannelTransactionId()).isEqualTo("CMR-001");
        assertThat(response.getRawResponse()).containsEntry("merchantRefundNo", "REFUND-001");
        assertThat(response.getRawResponse()).containsEntry("refundId", "CMR-001");
    }

    @Test
    void shouldMapPlatformProcessingCodesToProcessing() {
        responseBody.set("{\"code\":\"T202\",\"message\":\"Processing\",\"data\":{\"merchantOrderNo\":\"CMT-001\",\"transactionId\":\"TX-001\",\"responseCode\":\"T202\",\"currency\":\"USD\",\"amount\":10.25}}");
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("PAYMENT");
        request.setTransactionId("CMT-001");
        request.setMerchantOrderNo("SDKPAY-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("PROCESSING");
        assertThat(response.getChannelResponseCode()).isEqualTo("T202");
    }

    @Test
    void shouldMapMissingOrderQueryToDeterministicFailure() {
        responseStatus.set(404);
        responseBody.set("{\"code\":\"ORDER_NOT_FOUND\",\"message\":\"Order not found\"}");
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.setMerchantOrderNo("ORDER-MISSING");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("FAILED");
        assertThat(response.getChannelResponseCode()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(response.getRawChannelStatus()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void shouldCreateAuthorizationWithPlatformTransactionId() {
        responseStatus.set(201);
        responseBody.set(cardResponse("AUTH", null, "000101"));
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.AUTHORIZATION, "CMT-AUTH-001");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations");
        assertThat(parsePayload())
                .containsEntry("merchantOrderNo", "CMT-AUTH-001")
                .containsEntry("amount", new java.math.BigDecimal("10.25"))
                .containsEntry("currency", "USD")
                .doesNotContainKey("authorizationId");
        assertThat(response.getChannelOrderNo()).isEqualTo("CMA-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("CMT-CHANNEL-001");
        assertThat(response.getAuthCode()).isEqualTo("000101");
        assertThat(response.getAcquirerReferenceNo()).isNull();
    }

    @Test
    void shouldUsePreAuthorizationPath() {
        responseStatus.set(201);
        responseBody.set(cardResponse("PREAUTH", null, "000102"));
        PaymentApiClient client = new PaymentApiClient(properties());

        client.execute(cardRequest(ChannelCapability.PRE_AUTHORIZATION, "CMT-PREAUTH-001"));

        assertThat(requestPath).hasValue("/api/v7/card/preauthorizations");
    }

    @Test
    void shouldCaptureByAuthorizationIdAndMapArn() {
        responseStatus.set(201);
        responseBody.set(cardResponse("CAPTURE", "20261031000000000000101", "000103"));
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.CAPTURE, "CMT-CAPTURE-001");
        request.setChannelOrderNo("CMA-001");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/captures");
        assertThat(parsePayload())
                .containsEntry("authorizationId", "CMA-001")
                .containsEntry("merchantOrderNo", "CMT-CAPTURE-001")
                .doesNotContainKey("currency");
        assertThat(response.getAcquirerReferenceNo()).isEqualTo("20261031000000000000101");
        assertThat(response.getAuthCode()).isEqualTo("000103");
    }

    @Test
    void shouldUsePreAuthorizationCompletionPath() {
        responseStatus.set(201);
        responseBody.set(cardResponse("PREAUTH_COMPLETE", "20261031000000000000102", "000104"));
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.PRE_AUTH_COMPLETION, "CMT-COMPLETE-001");
        request.setChannelOrderNo("CMA-001");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/preauthorizations/completions");
    }

    @Test
    void shouldUseIncrementalAuthorizationPath() {
        responseStatus.set(201);
        responseBody.set(cardResponse("INCREMENT", null, "000105"));
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.INCREMENTAL_AUTHORIZATION, "CMT-INCREMENT-001");
        request.setChannelOrderNo("CMA-001");

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/increments");
    }

    @Test
    void shouldVoidWithoutAmount() {
        responseStatus.set(201);
        responseBody.set(cardResponse("VOID", null, null));
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = cardRequest(ChannelCapability.VOID, "CMT-VOID-001");
        request.setChannelOrderNo("CMA-001");
        request.setAmount(null);
        request.setCurrency(null);

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/voids");
        assertThat(parsePayload())
                .containsEntry("authorizationId", "CMA-001")
                .containsEntry("merchantOrderNo", "CMT-VOID-001")
                .doesNotContainKeys("amount", "currency");
        assertThat(response.getAuthCode()).isNull();
        assertThat(response.getAcquirerReferenceNo()).isNull();
    }

    @Test
    void shouldQueryCardActionAndMapAuthorizationReferences() {
        responseBody.set(cardResponse("CAPTURE", "20261031000000000000103", "000106"));
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(ChannelCapability.QUERY.getCode());
        request.setTransactionId("CMT-CAPTURE-QUERY-001");
        request.getExtension().put("originalTransactionType", ChannelCapability.CAPTURE.getCode());

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/query");
        assertThat(parsePayload())
                .containsOnlyKeys("merchantOrderNo")
                .containsEntry("merchantOrderNo", "CMT-CAPTURE-QUERY-001");
        assertThat(response.getChannelOrderNo()).isEqualTo("CMA-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("CMT-CHANNEL-001");
        assertThat(response.getAuthCode()).isEqualTo("000106");
        assertThat(response.getAcquirerReferenceNo()).isEqualTo("20261031000000000000103");
    }

    @Test
    void shouldQueryLatestCardActionByAuthorizationId() {
        responseBody.set(cardResponse("INCREMENT", null, "000107"));
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(ChannelCapability.QUERY.getCode());
        request.getExtension().put("authorizationId", "CMA-001");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/card/authorizations/query");
        assertThat(parsePayload())
                .containsOnlyKeys("authorizationId")
                .containsEntry("authorizationId", "CMA-001");
        assertThat(response.getAuthCode()).isEqualTo("000107");
        assertThat(response.getRawResponse()).containsEntry("authorizationCode", "000107");
    }

    @Test
    void shouldKeepMalformedSuccessfulResponseUncertain() {
        responseStatus.set(201);
        responseBody.set("not-json");
        PaymentApiClient client = new PaymentApiClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-MALFORMED")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    @Test
    void shouldKeepIncompleteAuthorizationResponseUncertain() {
        responseStatus.set(201);
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"status\":\"SUCCESS\"}}");
        PaymentApiClient client = new PaymentApiClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-INCOMPLETE")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    @Test
    void shouldKeepMissingSuccessfulStatusUncertain() {
        responseStatus.set(201);
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"transactionId\":\"CMT-CHANNEL-001\",\"authorizationId\":\"CMA-001\"}}");
        PaymentApiClient client = new PaymentApiClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-NO-STATUS")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    @Test
    void shouldNotInferChannelMoneyFromRequestWhenResponseOmitsIt() {
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"CMT-001\",\"transactionId\":\"TX-001\",\"authorizationId\":\"CMA-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\"}}");
        PaymentApiClient client = new PaymentApiClient(properties());

        ChannelPaymentResponse response = client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-NO-MONEY"));

        assertThat(response.getChannelAmount()).isNull();
        assertThat(response.getChannelCurrency()).isNull();
    }

    @Test
    void shouldMaskCardDataInRequestAudit() {
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setTransactionId("CMT-PAYMENT-MASK-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");
        request.setCardNo("4111111111111111");
        request.setSecurityCode("123");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getRequestBodyJsonMasked())
                .doesNotContain("4111111111111111")
                .doesNotContain("\"securityCode\":\"123\"")
                .contains("411111******1111")
                .contains("\"securityCode\":\"***\"");
    }

    @Test
    void shouldMergeOpenPaymentDataWithStandardCardFields() {
        PaymentApiClient client = new PaymentApiClient(properties());
        ChannelPaymentRequest request = cardlessPaymentRequest("CMT-PAYMENT-DATA-001");
        request.setPaymentMethod("BANK_CARD");
        request.setCardNo("4111111111111111");
        request.getExtension().put("paymentData", "{\"walletReference\":\"WALLET-001\"}");

        client.execute(request);

        Map<String, Object> payload = parsePayload();
        Map<?, ?> paymentData = (Map<?, ?>) payload.get("paymentData");
        assertThat(paymentData.get("walletReference")).isEqualTo("WALLET-001");
        assertThat(paymentData.get("cardNo")).isEqualTo("4111111111111111");
    }

    @Test
    void shouldKeepRateLimitOutcomeUncertain() {
        responseStatus.set(429);
        responseBody.set("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
        PaymentApiClient client = new PaymentApiClient(properties());

        assertThatThrownBy(() -> client.execute(cardRequest(
                ChannelCapability.AUTHORIZATION, "CMT-AUTH-RATE-LIMITED")))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    @Test
    void shouldIgnoreUnknownResponseFields() {
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"futureRoot\":true,\"data\":{\"merchantOrderNo\":\"CMT-001\",\"transactionId\":\"TX-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"futureData\":{\"nested\":true}}}");
        PaymentApiClient client = new PaymentApiClient(properties());

        ChannelPaymentResponse response = client.execute(cardlessPaymentRequest("CMT-001"));

        assertThat(response.getChannelTradeStatus()).isEqualTo("SUCCESS");
        assertThat(response.getChannelTransactionId()).isEqualTo("TX-001");
    }

    private ChannelPaymentRequest cardRequest(ChannelCapability capability, String transactionId) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(capability.getCode());
        request.setTransactionId(transactionId);
        request.setMerchantOrderNo("MERCHANT-ORDER-001");
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("BANK_CARD");
        return request;
    }

    private ChannelPaymentRequest cardlessPaymentRequest(String transactionId) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setTransactionId(transactionId);
        request.setAmount(new java.math.BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setPaymentMethod("UPI");
        return request;
    }

    private String refundResponse() {
        return "{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"CMT-ORIGINAL-PAYMENT\",\"transactionId\":\"CMT-ORIGINAL-PAYMENT\",\"merchantRefundNo\":\"REFUND-001\",\"refundId\":\"CMR-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"currency\":\"USD\",\"amount\":10.25}}";
    }

    private String cardResponse(String operationType, String arn, String authorizationCode) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("merchantOrderNo", "CMT-PLATFORM-001");
        data.put("transactionId", "CMT-CHANNEL-001");
        data.put("authorizationId", "CMA-001");
        data.put("operationType", operationType);
        data.put("amount", new java.math.BigDecimal("10.25"));
        data.put("currency", "USD");
        data.put("status", "SUCCESS");
        data.put("responseCode", "SUCCESS");
        data.put("responseDescription", "Processed");
        if (arn != null) {
            data.put("acquirerReferenceNumber", arn);
        }
        if (authorizationCode != null) {
            data.put("authorizationCode", authorizationCode);
        }
        return JsonUtils.toJsonString(Map.of("code", "SUCCESS", "message", "OK", "data", data));
    }

    private ChannelProperties properties() {
        ChannelProperties properties = new ChannelProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setVersion("v7");
        properties.setMerchantId("MID-TEST");
        properties.setApiKey("KEY-TEST");
        return properties;
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestPath.set(exchange.getRequestURI().getPath());
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus.get(), response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload() {
        return (Map<String, Object>) JsonUtils.parseObject(requestBody.get(), Map.class);
    }
}
