package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.Authenticator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsApiClientMaskingTests
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 日志脱敏测试，验证卡号、CVV、3DS authenticationToken 和渠道密码等敏感字段不会以明文进入请求响应日志。
 * @status : create
 */
@Slf4j
class MpgsApiClientMaskingTests {

    /**
     * 验证 MPGS JSON 脱敏规则：完整卡号、CVV、3DS authenticationToken 和 apiPassword 都不能以明文进入日志。
     */
    @Test
    void shouldMaskMpgsCardNumberAndAuthenticationToken() {
        String json = "{\"sourceOfFunds\":{\"provided\":{\"card\":{\"number\":\"5123450000000008\",\"securityCode\":\"100\"}}},"
                + "\"authentication\":{\"threeDs\":{\"authenticationToken\":\"AAABBIIFmAAAAAAAAAAAAAAAAAA=\"}},"
                + "\"apiPassword\":\"secret-value\"}";
        log.info("MPGS脱敏测试开始，case=卡号、CVV、authenticationToken、apiPassword");

        String masked = MpgsApiClient.maskMpgsJson(json);
        log.info("MPGS脱敏测试输出，masked={}", masked);

        assertThat(masked).contains("\"number\":\"512345******0008\"");
        assertThat(masked).contains("\"securityCode\":\"***\"");
        assertThat(masked).contains("\"authenticationToken\":\"***\"");
        assertThat(masked).contains("\"apiPassword\":\"***\"");
        assertThat(masked).doesNotContain("5123450000000008", "AAABBIIFmAAAAAAAAAAAAAAAAAA=", "secret-value");
    }

    /**
     * 验证查询类接口的空请求体可安全进入日志脱敏方法，不应出现空指针或伪造请求体。
     */
    @Test
    void shouldKeepBlankPayloadSafeForQueryLog() {
        log.info("MPGS脱敏测试开始，case=查询空请求体");

        String masked = MpgsApiClient.maskMpgsJson(null);

        assertThat(masked).isNull();
    }

    /**
     * 验证 MPGS MID 元数据标准字段 password 能用于 Basic Auth。
     * <p>
     * 后台保存的 metadata_value_json 字段为 password，支付核心组装渠道请求时会转成 mid.password；
     * 该字段必须优先于历史 apiPassword 读取，否则真实交易会在请求前失败。
     */
    @Test
    void shouldUseMidPasswordMetadataForBasicAuth() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        MpgsChannelProperties properties = new MpgsChannelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://test-gateway.mastercard.com/api/rest");
        properties.setVersion("100");
        properties.setMerchantId("TESTDEVMER031");
        properties.setReadTimeoutMillis(30000);
        properties.setConnectTimeoutMillis(10000);
        MpgsApiClient client = new MpgsApiClient(properties, new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);
        ChannelPaymentRequest request = paymentRequest();
        request.getExtension().put("mid.password", "metadata-password");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelResponseCode()).isEqualTo("APPROVED");
        assertThat(httpClient.authorizationHeader()).startsWith("Basic ");
        assertThat(httpClient.decodedAuthorization()).isEqualTo("merchant.TESTDEVMER031:metadata-password");
    }

    private ChannelPaymentRequest paymentRequest() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode("MPGS");
        request.setOperationId("OP202607170001");
        request.setTransactionId("202607170001");
        request.setChannelOrderNo("202607170001");
        request.setChannelTransactionId("CH202607170001");
        request.setMerchantId("200045");
        request.setMerchantOrderNo("M202607170001");
        request.setMerchantOrderId("REQ202607170001");
        request.setTransactionType(ChannelCapability.PAYMENT.getCode());
        request.setAmount(new BigDecimal("1.00"));
        request.setCurrency("USD");
        request.setTransactionDateTime(LocalDateTime.of(2026, 7, 17, 10, 30));
        request.setCardNo("5123450000000008");
        request.setExpirationMonth("01");
        request.setExpirationYear("2039");
        request.setSecurityCode("100");
        request.setCardBrand("MASTERCARD");
        return request;
    }

    private static class CapturingHttpClient extends HttpClient {

        private String authorizationHeader;

        private String authorizationHeader() {
            return authorizationHeader;
        }

        private String decodedAuthorization() {
            return new String(java.util.Base64.getDecoder().decode(authorizationHeader.substring("Basic ".length())));
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
            this.authorizationHeader = request.headers().firstValue("Authorization").orElse(null);
            @SuppressWarnings("unchecked")
            T body = (T) ("{\"result\":\"SUCCESS\",\"response\":{\"gatewayCode\":\"APPROVED\",\"gatewayRecommendation\":\"NO_ACTION\","
                    + "\"acquirerCode\":\"00\",\"acquirerMessage\":\"Approved\"},\"order\":{\"id\":\"202607170001\","
                    + "\"status\":\"CAPTURED\"},\"transaction\":{\"id\":\"CH202607170001\",\"type\":\"PAYMENT\","
                    + "\"authorizationCode\":\"123456\"}}");
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
    }

    private record SimpleHttpResponse<T>(HttpRequest request, T body) implements HttpResponse<T> {

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
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
