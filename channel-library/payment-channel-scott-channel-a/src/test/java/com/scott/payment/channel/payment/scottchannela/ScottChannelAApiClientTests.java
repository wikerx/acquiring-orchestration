package com.scott.payment.channel.payment.scottchannela;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAApiClientTests
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 上游路径、版本和查询标识测试，验证协议适配器不会同时发送互斥订单标识。
 * @status : create
 */
class ScottChannelAApiClientTests {

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
        ScottChannelAProperties properties = properties();
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties);
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
        ScottChannelAProperties properties = properties();
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties);
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
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties());
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
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties());
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

        client.execute(request);

        assertThat(requestPath).hasValue("/api/v7/payins/refunds");
        Map<String, Object> payload = parsePayload();
        assertThat(payload).containsEntry("transactionId", "CMT-ORIGINAL-PAYMENT");
        assertThat(payload).doesNotContainKey("merchantOrderNo");
        assertThat(payload).doesNotContainEntry("transactionId", "CH-REFUND-ACTION");
        assertThat(payload).doesNotContainEntry("transactionId", "CMT-REFUND-ACTION");
        assertThat(payload).containsEntry("merchantRefundNo", "REFUND-001");
    }

    /** 数据库存储的六位小数金额必须按渠道两位小数协议发送。 */
    @Test
    void shouldNormalizeRefundAmountToTwoDecimalPlaces() {
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties());
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

    /** 渠道明确返回 4xx 时请求已被拒绝，不能继续作为结果未知订单处理。 */
    @Test
    void shouldMapClientErrorToDeterministicFailure() {
        responseStatus.set(400);
        responseBody.set("{\"code\":\"INVALID_REQUEST\",\"message\":\"Request validation failed\",\"errors\":[\"amount: scale must be less than or equal to 2\"]}");
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties());
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

    /** 5xx 无法证明资金动作是否被渠道受理，仍需保持结果未知并后续勾兑。 */
    @Test
    void shouldKeepServerErrorOutcomeUncertain() {
        responseStatus.set(500);
        responseBody.set("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}");
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties());
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

    /** 退款查询必须调用退款查询接口，并使用 merchantRefundNo 作为唯一查询标识。 */
    @Test
    void shouldQueryRefundByMerchantRefundNoAndMapRefundId() {
        responseBody.set("{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"CMT-ORIGINAL-PAYMENT\",\"transactionId\":\"CMT-ORIGINAL-PAYMENT\",\"merchantRefundNo\":\"REFUND-001\",\"refundId\":\"CMR-001\",\"status\":\"SUCCESS\",\"responseCode\":\"SUCCESS\",\"currency\":\"USD\",\"amount\":10.25}}");
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties());
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
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties());
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
        ScottChannelAApiClient client = new ScottChannelAApiClient(properties());
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setTransactionType("QUERY");
        request.setMerchantOrderNo("ORDER-MISSING");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelTradeStatus()).isEqualTo("FAILED");
        assertThat(response.getChannelResponseCode()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(response.getRawChannelStatus()).isEqualTo("ORDER_NOT_FOUND");
    }

    private ScottChannelAProperties properties() {
        ScottChannelAProperties properties = new ScottChannelAProperties();
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
