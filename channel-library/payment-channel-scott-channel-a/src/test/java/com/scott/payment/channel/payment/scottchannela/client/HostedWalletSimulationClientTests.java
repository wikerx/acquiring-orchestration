package com.scott.payment.channel.payment.scottchannela.client;

import com.alibaba.fastjson2.JSON;
import com.scott.payment.channel.payment.api.HostedWalletSimulator.Order;
import com.scott.payment.channel.payment.api.HostedWalletSimulator.State;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostedWalletSimulationClientTests {

    private final HostedWalletSimulationClient client = new HostedWalletSimulationClient();

    @Test
    void acceptsConfiguredScottSimulatorAddressesAndRejectsMalformedRoutes() {
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "http://127.0.0.1:58090", "MID-200047"))
                .isTrue();
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "http://127.0.0.1:58090/", "MID-200048"))
                .isTrue();
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "http://127.0.0.1:58090", " "))
                .isFalse();
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "https://channel.example.test:8443/mock/", "MID-200047"))
                .isTrue();
        assertThat(client.supportsRoute("SCOTT_CHANNEL_A", "https://channel.example.test?mode=other", "MID-200047"))
                .isFalse();
        assertThat(client.supportsRoute("OTHER_CHANNEL", "http://127.0.0.1:58090", "MID-200047"))
                .isFalse();
    }

    @Test
    void usesRoutedBaseForRequestsAndRequiresApprovalFromTheSameBase() throws IOException {
        var simulator = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + simulator.getAddress().getPort() + "/mock";
        String apiPath = "/mock/api/v1/payins/hosted";
        String returnUrl = "https://checkout.example.test/wallet/return";
        String notifyUrl = "https://gateway.example.test/channel/notify";
        String approvalPath = "/simulate/hosted-payins/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        var approval = new AtomicReference<>(base + approvalPath);
        var requests = new CopyOnWriteArrayList<String>();
        simulator.createContext(apiPath, exchange -> {
            try (exchange) {
                requests.add(exchange.getRequestURI().getPath());
                byte[] response = JSON.toJSONString(Map.of("code", "SUCCESS", "data", Map.ofEntries(
                        Map.entry("merchantId", "TEST-MID"), Map.entry("merchantOrderNo", "TEST-ORDER"),
                        Map.entry("transactionId", "CMT-TEST-ORDER"), Map.entry("paymentMethod", "APPLE_PAY"),
                        Map.entry("currency", "USD"), Map.entry("amount", "3.10"), Map.entry("simulated", true),
                        Map.entry("walletMode", "INTERACTIVE_SIMULATION"), Map.entry("status", "PROCESSING"),
                        Map.entry("returnUrl", returnUrl), Map.entry("notifyUrl", notifyUrl),
                        Map.entry("nextAction", Map.of("type", "REDIRECT", "redirectUrl", approval.get())))))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        simulator.start();
        try {
            var order = new Order(base + "/", "test-fixture", "TEST-MID", "TEST-ORDER",
                    new BigDecimal("3.10"), "USD", "APPLE_PAY");
            assertThat(client.prepare(order, returnUrl, notifyUrl).redirectUrl()).isEqualTo(approval.get());
            assertThat(client.query(order).state()).isEqualTo(State.READY);
            assertThat(requests).containsExactly(apiPath, apiPath + "/query");
            approval.set("https://other-simulator.example.test/mock" + approvalPath);
            assertThatThrownBy(() -> client.prepare(order, returnUrl, notifyUrl))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            simulator.stop(0);
        }
    }
}
