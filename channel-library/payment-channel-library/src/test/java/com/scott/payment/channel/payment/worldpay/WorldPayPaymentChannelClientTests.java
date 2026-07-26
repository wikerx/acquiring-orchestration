package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayPaymentChannelClientTests
 * @date : 2026-07-19 23:30
 * @email : scott_x@163.com
 * @description : WorldPay 渠道客户端边界测试，验证 WPGXML 未接通时明确阻断，WPGJSON 能按后台 MID 配置发起 Access Worldpay JSON 请求并保留脱敏审计字段。
 * @status : create
 */
class WorldPayPaymentChannelClientTests {

    /**
     * WPGXML 当前只注册独立渠道编码和计划能力，真实 XML 请求未实现前不能发起支付。
     */
    @Test
    void shouldBlockWorldPayXmlRealPaymentBeforeApiConnected() {
        WorldPayXmlPaymentChannelClient client = new WorldPayXmlPaymentChannelClient();
        ChannelPaymentRequest request = request(PaymentChannelCode.WPGXML.getCode(), "PAYMENT");

        assertThat(client.channelCode()).isEqualTo(PaymentChannelCode.WPGXML.getCode());
        assertThatThrownBy(() -> client.payment(request))
                .isInstanceOf(WorldPayChannelNotImplementedException.class)
                .hasMessageContaining("WorldPay渠道[WPGXML]真实请求尚未接通")
                .hasMessageContaining("禁止用于生产交易能力[PAYMENT]");
    }

    /**
     * WPGJSON 应使用后台配置的 MID 三要素发起 Access Worldpay JSON 支付请求，并把渠道响应映射为统一响应。
     */
    @Test
    void shouldExecuteWorldPayJsonPaymentWithConfiguredMidCredentials() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayJsonApiClient apiClient = new WorldPayJsonApiClient(
                new WorldPayJsonRequestMapper(), new WorldPayJsonResponseMapper(), httpClient);
        WorldPayJsonPaymentChannelClient client = new WorldPayJsonPaymentChannelClient(apiClient);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(PaymentChannelCode.WPGJSON.getCode());
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setOperationId("OP-WPG-001");
        request.setTransactionId("TX-WPG-PAYMENT-001");
        request.setChannelOrderNo("ORDER-WPG-001");
        request.setChannelTransactionId("CH-WPG-PAYMENT-001");
        request.setMerchantId("200045");
        request.setMerchantOrderNo("MO-WPG-001");
        request.setAmount(new BigDecimal("12.34"));
        request.setCurrency("USD");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("1");
        request.setExpirationYear("30");
        request.setSecurityCode("100");
        request.setCardBrand("MASTERCARD");
        request.getExtension().put("requestId", "CR-WPG-001");
        request.getExtension().put("requestUrl", "https://try.access.worldpay.com");
        request.getExtension().put("currencyExponent", "2");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "json-user");
        request.getExtension().put("mid.password", "json-password");
        request.getExtension().put("mid.endpointPath", "/api/payments");

        assertThat(client.channelCode()).isEqualTo(PaymentChannelCode.WPGJSON.getCode());
        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getChannelCode()).isEqualTo(PaymentChannelCode.WPGJSON.getCode());
        assertThat(response.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(response.getChannelTradeStatus()).isEqualTo("SUCCESS");
        assertThat(response.getChannelResponseCode()).isEqualTo("sentForSettlement");
        assertThat(response.getChannelOrderNo()).isEqualTo("ORDER-WPG-001");
        assertThat(response.getChannelTransactionId()).isEqualTo("WP-PAYMENT-001");
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getRequestUrlMasked()).isEqualTo("https://try.access.worldpay.com/api/payments");
        assertThat(response.getRequestHeaderJsonMasked()).contains("Basic ***");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"transactionReference\":\"CH-WPG-PAYMENT-001\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"entity\":\"AWAPGTEST\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"amount\":1234");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"currency\":\"USD\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"type\":\"card/plain\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"cardNumber\":\"512345******0008\"");
        assertThat(response.getRequestBodyJsonMasked()).contains("\"cvc\":\"***\"");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("5123450000000008", "\"cvc\":\"100\"");
        assertThat(response.getRequestBodyJsonMasked()).doesNotContain("json-password");
        assertThat(response.getResponseHeaderJsonMasked()).contains("WP-CorrelationId");
        assertThat(response.getRawResponse()).containsEntry("wpCorrelationId", "WP-CORR-001");
        assertThat(response.getRawResponse()).containsEntry("worldpaySettleLink", "https://try.access.worldpay.com/api/payments/WP-PAYMENT-001/settlements");
        assertThat(httpClient.lastRequest().method()).isEqualTo("POST");
        assertThat(httpClient.lastRequest().headers().firstValue("Content-Type")).contains("application/vnd.worldpay.payments-v7+json");
        assertThat(httpClient.lastRequest().headers().firstValue("Accept")).contains("application/vnd.worldpay.payments-v7+json");
        assertThat(httpClient.decodedAuthorization()).isEqualTo("json-user:json-password");
        assertThat(httpClient.lastBody()).contains("\"transactionReference\":\"CH-WPG-PAYMENT-001\"");
        assertThat(httpClient.lastBody()).contains("\"requestAutoSettlement\":{\"enabled\":true}");
        assertThat(httpClient.lastBody()).contains("\"cardNumber\":\"5123450000000008\"");
    }

    /**
     * WPGJSON 后续请款必须使用 Worldpay action link 或显式配置路径，不能把平台交易号硬拼成渠道 URL。
     */
    @Test
    void shouldExecuteWorldPayJsonCaptureWithActionLink() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayJsonApiClient apiClient = new WorldPayJsonApiClient(
                new WorldPayJsonRequestMapper(), new WorldPayJsonResponseMapper(), httpClient);
        WorldPayJsonPaymentChannelClient client = new WorldPayJsonPaymentChannelClient(apiClient);
        ChannelCaptureRequest request = new ChannelCaptureRequest();
        request.setChannelCode(PaymentChannelCode.WPGJSON.getCode());
        request.setTransactionType(ChannelCapability.CAPTURE.getCode());
        request.setOperationId("OP-WPG-001");
        request.setTransactionId("TX-WPG-CAPTURE-001");
        request.setChannelOrderNo("ORDER-WPG-001");
        request.setChannelTransactionId("CH-WPG-CAPTURE-001");
        request.setAmount(new BigDecimal("12.34"));
        request.setCurrency("USD");
        request.getExtension().put("requestId", "CR-WPG-CAPTURE-001");
        request.getExtension().put("requestUrl", "https://try.access.worldpay.com");
        request.getExtension().put("currencyExponent", "2");
        request.getExtension().put("worldpaySettleLink", "https://try.access.worldpay.com/api/payments/WP-PAYMENT-001/settlements");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "json-user");
        request.getExtension().put("mid.password", "json-password");

        ChannelPaymentResponse response = client.capture(request);

        assertThat(response.getRawChannelStatus()).isEqualTo("CAPTURED");
        assertThat(httpClient.lastRequest().uri().toString())
                .isEqualTo("https://try.access.worldpay.com/api/payments/WP-PAYMENT-001/settlements");
        assertThat(httpClient.lastBody()).contains("\"transactionReference\":\"CH-WPG-CAPTURE-001\"");
        assertThat(httpClient.lastBody()).contains("\"amount\":1234");
        assertThat(httpClient.lastBody()).doesNotContain("cardNumber", "cvc");
    }

    /**
     * WPGJSON 最小单位金额应按 service-payment 从数据库币种表透传的辅币位换算，不能固定按两位小数处理。
     */
    @Test
    void shouldUsePropagatedCurrencyExponentWhenBuildingMinorAmount() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        WorldPayJsonApiClient apiClient = new WorldPayJsonApiClient(
                new WorldPayJsonRequestMapper(), new WorldPayJsonResponseMapper(), httpClient);
        WorldPayJsonPaymentChannelClient client = new WorldPayJsonPaymentChannelClient(apiClient);
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(PaymentChannelCode.WPGJSON.getCode());
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setTransactionId("TX-WPG-BHD-001");
        request.setChannelOrderNo("ORDER-WPG-BHD-001");
        request.setChannelTransactionId("CH-WPG-BHD-001");
        request.setAmount(new BigDecimal("12.345"));
        request.setCurrency("BHD");
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("1");
        request.setExpirationYear("30");
        request.setSecurityCode("100");
        request.getExtension().put("requestUrl", "https://try.access.worldpay.com");
        request.getExtension().put("currencyExponent", "3");
        request.getExtension().put("mid.channelMid", "AWAPGTEST");
        request.getExtension().put("mid.username", "json-user");
        request.getExtension().put("mid.password", "json-password");

        ChannelPaymentResponse response = client.payment(request);

        assertThat(response.getRequestBodyJsonMasked()).contains("\"amount\":12345");
        assertThat(httpClient.lastBody()).contains("\"currency\":\"BHD\"");
        assertThat(httpClient.lastBody()).contains("\"amount\":12345");
    }

    /**
     * WPGJSON 脱敏必须覆盖 cardNumber、cvc、cavv、password 和 Authorization，避免渠道请求日志泄露卡数据或认证凭据。
     */
    @Test
    void shouldMaskWorldPayJsonSensitiveFields() {
        String json = "{\"card\":{\"cardNumber\":\"5123450000000008\",\"cvc\":\"100\"},"
                + "\"threeDs\":{\"cavv\":\"AAABBIIFmAAAAAAAAAAAAAAAAAA=\"},"
                + "\"password\":\"secret-value\",\"Authorization\":\"Basic abcdef\"}";

        String masked = WorldPayJsonApiClient.maskWorldPayJson(json);

        assertThat(masked).contains("\"cardNumber\":\"512345******0008\"");
        assertThat(masked).contains("\"cvc\":\"***\"");
        assertThat(masked).contains("\"cavv\":\"***\"");
        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).contains("\"Authorization\":\"***\"");
        assertThat(masked).doesNotContain("5123450000000008", "\"cvc\":\"100\"", "AAABBIIFmAAAAAAAAAAAAAAAAAA=", "secret-value", "Basic abcdef");
    }

    /**
     * 查询勾兑任务存在不代表 WPGXML/WPGJSON Inquiry 已接通，查询入口也必须在真实实现前阻断。
     */
    @Test
    void shouldBlockWorldPayInquiryBeforeQueryApiConnected() {
        WorldPayXmlPaymentChannelClient client = new WorldPayXmlPaymentChannelClient();
        ChannelQueryRequest request = new ChannelQueryRequest();
        request.setChannelCode(PaymentChannelCode.WPGXML.getCode());
        request.setTransactionType("QUERY");

        assertThatThrownBy(() -> client.query(request))
                .isInstanceOf(WorldPayChannelNotImplementedException.class)
                .hasMessageContaining("真实请求尚未接通")
                .hasMessageContaining("生产交易能力[QUERY]");
    }

    private ChannelPaymentRequest request(String channelCode, String transactionType) {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode(channelCode);
        request.setTransactionType(transactionType);
        return request;
    }

    private static class CapturingHttpClient extends HttpClient {

        /**
         * 最近一次 HTTP 请求，用于断言 WPGJSON 客户端实际发出的 method、URL 和请求头。
         */
        private HttpRequest lastRequest;

        /**
         * 最近一次请求体，仅在测试中读取，生产日志不得输出未脱敏报文。
         */
        private String lastBody;

        private HttpRequest lastRequest() {
            return lastRequest;
        }

        private String lastBody() {
            return lastBody;
        }

        private String decodedAuthorization() {
            String authorization = lastRequest.headers().firstValue("Authorization").orElseThrow();
            return new String(java.util.Base64.getDecoder().decode(authorization.substring("Basic ".length())));
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(10));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, null, new SecureRandom());
                return context;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            this.lastRequest = request;
            this.lastBody = request.bodyPublisher()
                    .map(ignored -> requestBodyFromKnownPublisher(request))
                    .orElse("");
            @SuppressWarnings("unchecked")
            T body = (T) ("{\"outcome\":\"sentForSettlement\",\"paymentId\":\"WP-PAYMENT-001\","
                    + "\"orderCode\":\"ORDER-WPG-001\",\"requestId\":\"CR-WPG-001\","
                    + "\"authorizationCode\":\"123456\",\"stan\":\"654321\",\"rrn\":\"RRN-WPG-001\","
                    + "\"acquirerReference\":\"ARN-WPG-001\","
                    + "\"paymentInstrument\":{\"type\":\"CARD\",\"brand\":\"MASTERCARD\","
                    + "\"cardNumberMasked\":\"512345******0008\"},"
                    + "\"_links\":{\"cardPayments:settle\":{\"href\":\"https://try.access.worldpay.com/api/payments/WP-PAYMENT-001/settlements\",\"method\":\"POST\"},"
                    + "\"payments:events\":{\"href\":\"https://try.access.worldpay.com/api/payments/events?transactionRef=CH-WPG-PAYMENT-001&entity=AWAPGTEST\",\"method\":\"GET\"}}}");
            return new SimpleHttpResponse<>(request, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("async not used"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("async not used"));
        }

        private String requestBodyFromKnownPublisher(HttpRequest request) {
            java.util.concurrent.Flow.Publisher<java.nio.ByteBuffer> publisher = request.bodyPublisher().orElse(null);
            if (publisher == null) {
                return "";
            }
            BodySubscriber subscriber = new BodySubscriber();
            publisher.subscribe(subscriber);
            return subscriber.body();
        }
    }

    private static class BodySubscriber implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {

        /**
         * 请求体缓冲区，仅用于单元测试读取 JDK HttpRequest BodyPublisher 内容。
         */
        private final StringBuilder body = new StringBuilder();

        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(java.nio.ByteBuffer item) {
            body.append(StandardCharsets.UTF_8.decode(item));
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        @Override
        public void onComplete() {
        }

        private String body() {
            return body.toString();
        }
    }

    private record SimpleHttpResponse<T>(HttpRequest request, T body) implements HttpResponse<T> {

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(
                    "WP-CorrelationId", java.util.List.of("WP-CORR-001"),
                    "Content-Type", java.util.List.of("application/vnd.worldpay.payments-v7+json")
            ), (name, value) -> true);
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}
