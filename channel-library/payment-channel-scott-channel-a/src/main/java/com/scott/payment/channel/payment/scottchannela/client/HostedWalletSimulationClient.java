package com.scott.payment.channel.payment.scottchannela.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.scott.payment.channel.payment.api.HostedWalletSimulator;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** ScottChannelA 挡板协议，通过渠道路由配置访问，钱包凭证只在服务端调用栈内流转。 */
public final class HostedWalletSimulationClient implements HostedWalletSimulator {
    private static final String API_PATH = "/api/v1/payins/hosted";
    private static final String AUTHORIZATION_PATH = "/simulate/hosted-payins/";
    private static final String MODE = "INTERACTIVE_SIMULATION";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public boolean supportsRoute(String channelCode, String requestUrl, String midNo) {
        return "SCOTT_CHANNEL_A".equals(channelCode)
                && midNo != null && !midNo.isBlank() && midNo.length() <= 64
                && simulatorBase(requestUrl) != null;
    }

    @Override
    public Result prepare(Order order, String returnUrl, String notifyUrl) {
        JSONObject data = checked(post(order, "", Map.of(
                "merchantOrderNo", order.number(), "amount", order.amount(), "currency", order.currency(),
                "countryCode", "US", "paymentMethod", order.method(), "walletMode", MODE,
                "returnUrl", returnUrl, "notifyUrl", notifyUrl, "expiresInSeconds", 600)), order);
        if (!returnUrl.equals(data.getString("returnUrl")) || !notifyUrl.equals(data.getString("notifyUrl"))) {
            throw new SimulationUnavailable();
        }
        return snapshot(data, order);
    }

    @Override
    public Result query(Order order) {
        return snapshot(queryData(order), order);
    }

    @Override
    public Result submitApproved(Order order) {
        JSONObject current = queryData(order);
        Result before = snapshot(current, order);
        if (before.state() != State.APPROVED) {
            return before;
        }
        String transactionId = before.transactionId();
        Object credential;
        if ("APPLE_PAY".equals(order.method())) {
            JSONObject params = current.getJSONObject("nextAction").getJSONObject("publicParameters");
            JSONObject merchant = post(order, "/apple-pay/merchant-session", Map.of(
                    "transactionId", transactionId, "validationUrl", params.getString("validationUrl"),
                    "initiativeContext", params.getString("initiativeContext")));
            JSONObject wallet = post(order, "/simulate/wallet-authorize", Map.of("transactionId", transactionId,
                    "merchantSessionId", merchant.getJSONObject("merchantSession").getString("merchantSessionIdentifier")));
            credential = wallet.getJSONObject("applePayment").getJSONObject("token");
        } else {
            JSONObject wallet = post(order, "/simulate/wallet-authorize", Map.of("transactionId", transactionId));
            credential = wallet.getJSONObject("googlePaymentData").getJSONObject("paymentMethodData")
                    .getJSONObject("tokenizationData").getString("token");
        }
        try {
            return snapshot(checked(post(order, "/submit", Map.of("transactionId", transactionId,
                    "idempotencyKey", order.number(), "credentialType", order.method() + "_TOKEN",
                    "credential", credential)), order), order);
        } catch (SimulationUnavailable exception) {
            // 响应异常时查询原单；同一次调用绝不重发提交。
            return query(order);
        }
    }

    private JSONObject queryData(Order order) {
        return checked(post(order, "/query", Map.of("merchantOrderNo", order.number())), order);
    }

    private JSONObject checked(JSONObject data, Order order) {
        String transactionId = data.getString("transactionId");
        if (!order.channelMerchantId().equals(data.getString("merchantId"))
                || !order.number().equals(data.getString("merchantOrderNo"))
                || !order.method().equals(data.getString("paymentMethod"))
                || !order.currency().equals(data.getString("currency"))
                || !MODE.equals(data.getString("walletMode"))
                || transactionId == null || !transactionId.matches("[A-Za-z0-9_-]{8,40}")
                || data.getBigDecimal("amount") == null || order.amount().compareTo(data.getBigDecimal("amount")) != 0) {
            throw new SimulationUnavailable();
        }
        return data;
    }

    private Result snapshot(JSONObject data, Order order) {
        JSONObject next = data.getJSONObject("nextAction");
        String code = data.getString("responseCode");
        String action = next == null ? "" : next.getString("type", "");
        State state = switch (data.getString("status", "")) {
            case "SUCCESS" -> State.SUCCESS;
            case "FAIL" -> "PAYMENT_EXPIRED".equals(code) ? State.EXPIRED : State.FAILED;
            case "CANCELLED" -> State.CANCELLED;
            case "PROCESSING" -> switch (action) {
                case "REDIRECT" -> State.READY;
                case "SUBMIT_AUTHORIZATION" -> State.APPROVED;
                case "WAIT_FOR_RESULT" -> State.PROCESSING;
                default -> State.UNKNOWN;
            };
            default -> State.UNKNOWN;
        };
        String redirect = state == State.READY
                ? requireApprovalUrl(order, next.getString("redirectUrl")) : null;
        return new Result(state, data.getString("transactionId"), redirect, code);
    }

    private String requireApprovalUrl(Order order, String value) {
        URI base = requireSimulatorBase(order);
        if (!OutboundHttpUrlPolicy.isStructurallyValid(value)) {
            throw new SimulationUnavailable();
        }
        URI uri = URI.create(value);
        String prefix = base.getRawPath() + AUTHORIZATION_PATH;
        String path = uri.getRawPath();
        if (!base.getScheme().equalsIgnoreCase(uri.getScheme()) || !base.getHost().equalsIgnoreCase(uri.getHost())
                || effectivePort(base) != effectivePort(uri) || uri.getRawQuery() != null
                || !path.startsWith(prefix) || !path.substring(prefix.length()).matches("[a-f0-9-]{32,36}")) {
            throw new SimulationUnavailable();
        }
        return uri.toString();
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
    }

    private static URI simulatorBase(String value) {
        if (!OutboundHttpUrlPolicy.isStructurallyValid(value)) {
            return null;
        }
        URI uri = URI.create(value);
        String path = uri.getRawPath();
        if (uri.getRawQuery() != null || !path.matches("(?:/[A-Za-z0-9_-]+)*/?")) {
            return null;
        }
        return path.endsWith("/") ? URI.create(value.substring(0, value.length() - 1)) : uri;
    }

    private static URI requireSimulatorBase(Order order) {
        URI base = simulatorBase(order.channelBaseUrl());
        if (base == null) {
            throw new SimulationUnavailable();
        }
        return base;
    }

    private JSONObject post(Order order, String suffix, Map<String, ?> body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(requireSimulatorBase(order) + API_PATH + suffix))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .header("X-Merchant-Id", order.channelMerchantId())
                .header("X-Api-Key", order.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(body))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new SimulationUnavailable();
            }
            JSONObject payload = JSON.parseObject(response.body());
            JSONObject data = payload.getJSONObject("data");
            if (!"SUCCESS".equals(payload.getString("code")) || data == null
                    || !Boolean.TRUE.equals(data.getBoolean("simulated"))) {
                throw new SimulationUnavailable();
            }
            return data;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SimulationUnavailable();
        } catch (IOException | RuntimeException exception) {
            // 付款人错误不包含渠道原文、鉴权或钱包凭证。
            throw new SimulationUnavailable();
        }
    }

    private static final class SimulationUnavailable extends RuntimeException {
    }
}
