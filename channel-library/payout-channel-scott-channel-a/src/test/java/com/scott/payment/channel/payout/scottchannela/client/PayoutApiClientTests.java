package com.scott.payment.channel.payout.scottchannela.client;

import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutCancelRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.exception.PayoutChannelException;
import com.scott.payment.channel.payout.scottchannela.config.ChannelProperties;
import com.scott.payment.channel.payout.scottchannela.support.ChannelCode;
import com.scott.payment.component.core.json.JsonUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScottChannelA 代付请求映射、结果确定性和敏感信息脱敏测试。
 **/
class PayoutApiClientTests {

    /**
     * 代付缺少 beneficiaryReference 时必须在 Provider 边界拒绝。
     **/
    @Test
    void shouldRejectPayoutWithoutBeneficiaryReference() {
        ChannelPayoutRequest request = new ChannelPayoutRequest();
        request.setMerchantOrderNo("ORDER-001");
        request.setAmount(new BigDecimal("10.25"));
        request.setCurrency("USD");

        PayoutApiClient apiClient = new PayoutApiClient(new ChannelProperties());

        assertThatThrownBy(() -> apiClient.submit(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("beneficiary");
    }

    /**
     * 查询请求必须按上游契约只发送 merchantOrderNo 或 transactionId 之一。
     **/
    @Test
    void shouldSendSingleIdentifierForPayoutQuery() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, path, body));
        server.start();
        try {
            ChannelProperties properties = new ChannelProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setVersion("v3");
            properties.setMerchantId("MID-TEST");
            properties.setApiKey("KEY-TEST");
            PayoutApiClient client = new PayoutApiClient(properties);
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelOrderNo("PAYOUT-001");

            client.query(request);

            assertThat(path).hasValue("/api/v3/payouts/query");
            Map<String, Object> payload = parsePayload(body.get());
            assertThat(payload).containsEntry("merchantOrderNo", "PAYOUT-001");
            assertThat(payload).doesNotContainKey("transactionId");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 渠道默认请求地址优先于历史 MID 级 apiBaseUrl，避免账号元数据覆盖渠道路由地址。
     **/
    @Test
    void shouldUseChannelRequestUrlInsteadOfMidApiBaseUrl() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, path, body));
        server.start();
        try {
            ChannelProperties properties = properties(server);
            PayoutApiClient client = new PayoutApiClient(properties);
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelOrderNo("PAYOUT-CHANNEL-URL");
            request.getExtension().put("requestUrl", properties.getBaseUrl());
            request.getExtension().put("mid.apiBaseUrl", "http://127.0.0.1:1");

            client.query(request);

            assertThat(path).hasValue("/api/v3/payouts/query");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 标准字段优先于扩展字段，并把平台银行卡编码映射为渠道 CARD。
     **/
    @Test
    void shouldPreferStandardPaymentMethodAndMapBankCard() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, new AtomicReference<>(), body));
        server.start();
        try {
            ChannelProperties properties = properties(server);
            PayoutApiClient client = new PayoutApiClient(properties);
            ChannelPayoutRequest request = payoutRequest();
            request.setPaymentMethod("BANK_CARD");
            request.getExtension().put("paymentMethod", "CASH_APP_PAY");

            client.submit(request);

            assertThat(parsePayload(body.get())).containsEntry("paymentMethod", "CARD");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldUsePlatformPayoutOrderNoAsChannelIdempotencyKey() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, new AtomicReference<>(), body));
        server.start();
        try {
            PayoutApiClient client = new PayoutApiClient(properties(server));
            ChannelPayoutRequest request = payoutRequest();
            request.setPayoutOrderNo("PO-PLATFORM-001");
            request.setMerchantOrderNo("MERCHANT-ORDER-001");
            request.setPaymentMethod("ACH_DEBIT");
            request.getExtension().put("simulationResult", "SUCCESS");

            client.submit(request);

            assertThat(parsePayload(body.get()))
                    .containsEntry("merchantOrderNo", "PO-PLATFORM-001")
                    .containsEntry("simulationResult", "SUCCESS");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldUseSingleIdentifierForPayoutCancel() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, path, body));
        server.start();
        try {
            PayoutApiClient client = new PayoutApiClient(properties(server));
            ChannelPayoutCancelRequest request = new ChannelPayoutCancelRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelTransactionId("TX-001");

            client.cancel(request);

            assertThat(path).hasValue("/api/v3/payouts/cancel");
            assertThat(parsePayload(body.get()))
                    .containsOnlyKeys("transactionId")
                    .containsEntry("transactionId", "TX-001");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 兼容历史扩展字段中的 Cash App 别名，并统一映射为渠道 CASHAPP。
     **/
    @Test
    void shouldMapCashAppAliasFromExtension() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, new AtomicReference<>(), body));
        server.start();
        try {
            ChannelProperties properties = properties(server);
            PayoutApiClient client = new PayoutApiClient(properties);
            ChannelPayoutRequest request = payoutRequest();
            request.getExtension().put("paymentMethod", "CASH_APP_PAY");

            client.submit(request);

            assertThat(parsePayload(body.get())).containsEntry("paymentMethod", "CASHAPP");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 代付审计摘要必须隐藏渠道 API Key，避免连字符 Header 绕过通用 JSON 脱敏规则。
     **/
    @Test
    void shouldMaskPayoutApiKeyInRequestHeaderSummary() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, new AtomicReference<>(), body));
        server.start();
        try {
            ChannelProperties properties = properties(server);
            properties.setApiKey("SECRET-API-KEY");
            PayoutApiClient client = new PayoutApiClient(properties);
            ChannelPayoutRequest request = payoutRequest();
            request.setPaymentMethod("ACH_DEBIT");

            ChannelPayoutResponse response = client.submit(request);

            assertThat(response.getRequestHeaderJsonMasked()).contains("\"X-Api-Key\":\"***\"");
            assertThat(response.getRequestHeaderJsonMasked()).doesNotContain("SECRET-API-KEY");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldMapDocumentedClientErrorToDeterministicFailure() throws IOException {
        HttpServer server = responseServer(400,
                "{\"code\":\"INVALID_REQUEST\",\"message\":\"Request validation failed\"}");
        try {
            PayoutApiClient client = new PayoutApiClient(properties(server));
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelOrderNo("PO-001");

            ChannelPayoutResponse response = client.query(request);

            assertThat(response.getChannelPayoutStatus()).isEqualTo("FAILED");
            assertThat(response.getChannelResponseCode()).isEqualTo("INVALID_REQUEST");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepMalformedSuccessfulResponseUncertain() throws IOException {
        HttpServer server = responseServer(200, "not-json");
        try {
            PayoutApiClient client = new PayoutApiClient(properties(server));
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelOrderNo("PO-001");

            assertThatThrownBy(() -> client.query(request))
                    .isInstanceOfSatisfying(PayoutChannelException.class,
                            exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepServerErrorOutcomeUncertain() throws IOException {
        HttpServer server = responseServer(500,
                "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}");
        try {
            PayoutApiClient client = new PayoutApiClient(properties(server));
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelOrderNo("PO-001");

            assertThatThrownBy(() -> client.query(request))
                    .isInstanceOfSatisfying(PayoutChannelException.class,
                            exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectMalformedPaymentDataBeforeSending() {
        ChannelPayoutRequest request = payoutRequest();
        request.setPaymentMethod("ACH_DEBIT");
        request.getExtension().put("paymentData", "not-json");

        assertThatThrownBy(() -> new PayoutApiClient(new ChannelProperties())
                .submit(request))
                .isInstanceOfSatisfying(PayoutChannelException.class, exception -> {
                    assertThat(exception.isOutcomeUncertain()).isFalse();
                    assertThat(exception.getMessage()).contains("paymentData");
                });
    }

    /**
     * 渠道增加根级或 data 内字段时，Fastjson2 应忽略未知字段并保留已知交易证据。
     **/
    @Test
    void shouldIgnoreUnknownPayoutResponseFields() throws IOException {
        HttpServer server = responseServer(200, """
                {
                  "code":"SUCCESS",
                  "futureRoot":{"version":2},
                  "data":{
                    "merchantOrderNo":"PO-001",
                    "transactionId":"TX-001",
                    "status":"PROCESSING",
                    "futureData":{"providerTrace":"TRACE-001"}
                  }
                }
                """);
        try {
            PayoutApiClient client = new PayoutApiClient(properties(server));
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelOrderNo("PO-001");

            ChannelPayoutResponse response = client.query(request);

            assertThat(response.getChannelPayoutStatus()).isEqualTo("PROCESSING");
            assertThat(response.getChannelTransactionId()).isEqualTo("TX-001");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 金额、币种和描述是可选证据，缺失时不得阻断有效的代付状态查询。
     **/
    @Test
    void shouldAllowMissingOptionalPayoutResponseFields() throws IOException {
        HttpServer server = responseServer(200, """
                {
                  "code":"SUCCESS",
                  "data":{
                    "merchantOrderNo":"PO-001",
                    "transactionId":"TX-001",
                    "status":"SUCCESS"
                  }
                }
                """);
        try {
            PayoutApiClient client = new PayoutApiClient(properties(server));
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelOrderNo("PO-001");

            ChannelPayoutResponse response = client.query(request);

            assertThat(response.getChannelPayoutStatus()).isEqualTo("SUCCESS");
            assertThat(response.getChannelAmount()).isNull();
            assertThat(response.getChannelCurrency()).isNull();
        } finally {
            server.stop(0);
        }
    }

    /**
     * 成功 HTTP 响应缺少渠道交易身份时无法确认资金结果，必须进入 unknown 勾兑流程。
     **/
    @Test
    void shouldKeepIncompletePayoutResponseUncertain() throws IOException {
        HttpServer server = responseServer(200,
                "{\"code\":\"SUCCESS\",\"data\":{\"status\":\"SUCCESS\"}}");
        try {
            PayoutApiClient client = new PayoutApiClient(properties(server));
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ChannelCode.VALUE);
            request.setChannelOrderNo("PO-001");

            assertThatThrownBy(() -> client.query(request))
                    .isInstanceOfSatisfying(PayoutChannelException.class,
                            exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
        } finally {
            server.stop(0);
        }
    }

    private ChannelProperties properties(HttpServer server) {
        ChannelProperties properties = new ChannelProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setVersion("v3");
        properties.setMerchantId("MID-TEST");
        properties.setApiKey("KEY-TEST");
        return properties;
    }

    private ChannelPayoutRequest payoutRequest() {
        ChannelPayoutRequest request = new ChannelPayoutRequest();
        request.setChannelCode(ChannelCode.VALUE);
        request.setMerchantOrderNo("PAYOUT-001");
        request.setAmount(new BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setBeneficiaryReference("vault://beneficiary-001");
        return request;
    }

    private HttpServer responseServer(int status, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange, AtomicReference<String> path, AtomicReference<String> body)
            throws IOException {
        path.set(exchange.getRequestURI().getPath());
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = "{\"code\":\"SUCCESS\",\"message\":\"OK\",\"futureRoot\":true,\"data\":{\"merchantOrderNo\":\"PAYOUT-001\",\"transactionId\":\"TX-001\",\"status\":\"PROCESSING\",\"currency\":\"USD\",\"amount\":10.25,\"futureData\":{\"nested\":true}}}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String body) {
        return (Map<String, Object>) JsonUtils.parseObject(body, Map.class);
    }
}
