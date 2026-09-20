package com.scott.payment.channel.payout.scottchannela;

import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAPayoutClientTests
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 代付 Provider 基础校验测试，确保缺少收款人引用时不发起渠道请求。
 * @status : create
 */
class ScottChannelAPayoutClientTests {

    /** 代付缺少 beneficiaryReference 时必须在 Provider 边界拒绝。 */
    @Test
    void shouldRejectPayoutWithoutBeneficiaryReference() {
        ChannelPayoutRequest request = new ChannelPayoutRequest();
        request.setMerchantOrderNo("ORDER-001");
        request.setAmount(new BigDecimal("10.25"));
        request.setCurrency("USD");

        ScottChannelAPayoutApiClient apiClient = new ScottChannelAPayoutApiClient(new ScottChannelAPayoutProperties());

        assertThatThrownBy(() -> apiClient.submit(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("beneficiary");
    }

    /** 查询请求必须按上游契约只发送 merchantOrderNo 或 transactionId 之一。 */
    @Test
    void shouldSendSingleIdentifierForPayoutQuery() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, path, body));
        server.start();
        try {
            ScottChannelAPayoutProperties properties = new ScottChannelAPayoutProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setVersion("v3");
            properties.setMerchantId("MID-TEST");
            properties.setApiKey("KEY-TEST");
            ScottChannelAPayoutApiClient client = new ScottChannelAPayoutApiClient(properties);
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ScottChannelAPayoutCode.CODE);
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

    /** 渠道默认请求地址优先于历史 MID 级 apiBaseUrl，避免账号元数据覆盖渠道路由地址。 */
    @Test
    void shouldUseChannelRequestUrlInsteadOfMidApiBaseUrl() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, path, body));
        server.start();
        try {
            ScottChannelAPayoutProperties properties = properties(server);
            ScottChannelAPayoutApiClient client = new ScottChannelAPayoutApiClient(properties);
            ChannelPayoutQueryRequest request = new ChannelPayoutQueryRequest();
            request.setChannelCode(ScottChannelAPayoutCode.CODE);
            request.setChannelOrderNo("PAYOUT-CHANNEL-URL");
            request.getExtension().put("requestUrl", properties.getBaseUrl());
            request.getExtension().put("mid.apiBaseUrl", "http://127.0.0.1:1");

            client.query(request);

            assertThat(path).hasValue("/api/v3/payouts/query");
        } finally {
            server.stop(0);
        }
    }

    /** 标准字段优先于扩展字段，并把平台银行卡编码映射为渠道 CARD。 */
    @Test
    void shouldPreferStandardPaymentMethodAndMapBankCard() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, new AtomicReference<>(), body));
        server.start();
        try {
            ScottChannelAPayoutProperties properties = properties(server);
            ScottChannelAPayoutApiClient client = new ScottChannelAPayoutApiClient(properties);
            ChannelPayoutRequest request = payoutRequest();
            request.setPaymentMethod("BANK_CARD");
            request.getExtension().put("paymentMethod", "CASH_APP_PAY");

            client.submit(request);

            assertThat(parsePayload(body.get())).containsEntry("paymentMethod", "CARD");
        } finally {
            server.stop(0);
        }
    }

    /** 兼容历史扩展字段中的 Cash App 别名，并统一映射为渠道 CASHAPP。 */
    @Test
    void shouldMapCashAppAliasFromExtension() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, new AtomicReference<>(), body));
        server.start();
        try {
            ScottChannelAPayoutProperties properties = properties(server);
            ScottChannelAPayoutApiClient client = new ScottChannelAPayoutApiClient(properties);
            ChannelPayoutRequest request = payoutRequest();
            request.getExtension().put("paymentMethod", "CASH_APP_PAY");

            client.submit(request);

            assertThat(parsePayload(body.get())).containsEntry("paymentMethod", "CASHAPP");
        } finally {
            server.stop(0);
        }
    }

    /** 代付审计摘要必须隐藏渠道 API Key，避免连字符 Header 绕过通用 JSON 脱敏规则。 */
    @Test
    void shouldMaskPayoutApiKeyInRequestHeaderSummary() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange, new AtomicReference<>(), body));
        server.start();
        try {
            ScottChannelAPayoutProperties properties = properties(server);
            properties.setApiKey("SECRET-API-KEY");
            ScottChannelAPayoutApiClient client = new ScottChannelAPayoutApiClient(properties);
            ChannelPayoutRequest request = payoutRequest();
            request.setPaymentMethod("ACH_DEBIT");

            ChannelPayoutResponse response = client.submit(request);

            assertThat(response.getRequestHeaderJsonMasked()).contains("\"X-Api-Key\":\"***\"");
            assertThat(response.getRequestHeaderJsonMasked()).doesNotContain("SECRET-API-KEY");
        } finally {
            server.stop(0);
        }
    }

    private ScottChannelAPayoutProperties properties(HttpServer server) {
        ScottChannelAPayoutProperties properties = new ScottChannelAPayoutProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setVersion("v3");
        properties.setMerchantId("MID-TEST");
        properties.setApiKey("KEY-TEST");
        return properties;
    }

    private ChannelPayoutRequest payoutRequest() {
        ChannelPayoutRequest request = new ChannelPayoutRequest();
        request.setChannelCode(ScottChannelAPayoutCode.CODE);
        request.setMerchantOrderNo("PAYOUT-001");
        request.setAmount(new BigDecimal("10.25"));
        request.setCurrency("USD");
        request.setBeneficiaryReference("vault://beneficiary-001");
        return request;
    }

    private void handle(HttpExchange exchange, AtomicReference<String> path, AtomicReference<String> body)
            throws IOException {
        path.set(exchange.getRequestURI().getPath());
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = "{\"code\":\"SUCCESS\",\"message\":\"OK\",\"data\":{\"merchantOrderNo\":\"PAYOUT-001\",\"transactionId\":\"TX-001\",\"status\":\"PROCESSING\",\"currency\":\"USD\",\"amount\":10.25}}".getBytes(StandardCharsets.UTF_8);
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
