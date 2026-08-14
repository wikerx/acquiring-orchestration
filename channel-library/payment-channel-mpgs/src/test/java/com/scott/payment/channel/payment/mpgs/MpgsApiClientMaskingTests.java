package com.scott.payment.channel.payment.mpgs;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                + "\"expiry\":{\"month\":\"01\",\"year\":\"39\"},"
                + "\"cardholderName\":\"Jane Doe\",\"nameOnCard\":\"Jane Doe MPGS\","
                + "\"ipAddress\":\"203.0.113.9\",\"billingAddress\":\"1 Main Street\","
                + "\"apiPassword\":\"secret-value\"}";
        log.info("MPGS脱敏测试开始，case=卡号、安全码、认证令牌、渠道密码");

        String masked = MpgsApiClient.maskMpgsJson(json);
        log.info("MPGS脱敏测试输出，masked: {}", masked);

        assertThat(masked).contains("\"number\":\"512345******0008\"");
        assertThat(masked).contains("\"securityCode\":\"***\"");
        assertThat(masked).contains("\"authenticationToken\":\"***\"");
        assertThat(masked).contains("\"expiry\":{\"month\":\"***\",\"year\":\"***\"}");
        assertThat(masked).contains("\"cardholderName\":\"***\"");
        assertThat(masked).contains("\"nameOnCard\":\"***\"");
        assertThat(masked).contains("\"ipAddress\":\"***\"");
        assertThat(masked).contains("\"billingAddress\":\"***\"");
        assertThat(masked).contains("\"apiPassword\":\"***\"");
        assertThat(masked).doesNotContain("5123450000000008", "\"securityCode\":\"100\"",
                "AAABBIIFmAAAAAAAAAAAAAAAAAA=", "Jane Doe", "Jane Doe MPGS", "203.0.113.9", "1 Main Street",
                "\"month\":\"01\"", "\"year\":\"39\"", "secret-value");
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
     * 3DS returnUrl 会写入 MPGS 请求体，URL query 中的一次性回跳 token 必须先脱敏再进入日志。
     */
    @Test
    void shouldMaskThreeDsReturnUrlQuerySecrets() {
        String json = "{\"authentication\":{\"redirectResponseUrl\":\"https://pay.example.com/checkout/api/v1/3ds/bridge"
                + "?checkoutSessionId=CS-001&checkoutAttemptId=CA-001&threeDsReturnToken=return-token-value"
                + "&threeDSSessionData=session-secret-value\","
                + "\"redirect\":{\"html\":\"<form><input name=creq value=sensitive-creq></form>\"}},"
                + "\"encoded\":\"threeDsReturnToken%3Dencoded-token-value%26cres%3Dencoded-cres-value\"}";

        String masked = MpgsApiClient.maskMpgsJson(json);

        assertThat(masked).contains("threeDsReturnToken=***", "threeDSSessionData=***");
        assertThat(masked).contains("threeDsReturnToken%3D***", "cres%3D***");
        assertThat(masked).doesNotContain("return-token-value", "session-secret-value", "encoded-token-value", "encoded-cres-value");
        assertThat(masked).contains("\"html\":\"***\"");
    }

    /**
     * 验证数据库渠道配置完整时可直接调用 MPGS，不依赖额外的环境启用开关或渠道兜底配置。
     * <p>
     * 支付核心会把 MID 中的请求地址、API 版本、通用 MID 号和密码转换为 requestUrl、midNo 与 mid.* 扩展字段。
     * 渠道客户端应以这些路由结果为本次交易事实，避免数据库已启用渠道后仍被环境开关阻断。
     * </p>
     */
    @Test
    void shouldExecuteWithCompleteDatabaseRouteConfiguration() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        MpgsChannelProperties properties = new MpgsChannelProperties();
        properties.setReadTimeoutMillis(30000);
        properties.setConnectTimeoutMillis(10000);
        MpgsApiClient client = new MpgsApiClient(properties, new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);
        ChannelPaymentRequest request = paymentRequest();
        request.getExtension().put("requestUrl", "https://test-gateway.mastercard.com/api/rest");
        request.getExtension().put("mid.version", "100");
        request.getExtension().put("midNo", "TESTDEVMER031");
        request.getExtension().put("mid.password", "metadata-password");

        ChannelPaymentResponse response = client.execute(request);

        assertThat(response.getChannelResponseCode()).isEqualTo("00");
        assertThat(httpClient.authorizationHeader()).startsWith("Basic ");
        assertThat(httpClient.decodedAuthorization()).isEqualTo("merchant.TESTDEVMER031:metadata-password");
    }

    /**
     * 渠道应用日志只能记录不可逆摘要和长度，完整请求响应结构仅允许进入脱敏审计字段。
     */
    @Test
    void shouldLogOnlyChannelPayloadMetadata() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        MpgsChannelProperties properties = new MpgsChannelProperties();
        properties.setReadTimeoutMillis(30000);
        properties.setConnectTimeoutMillis(10000);
        MpgsApiClient client = new MpgsApiClient(properties, new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);
        ChannelPaymentRequest request = paymentRequest();
        request.getExtension().put("requestUrl", "https://test-gateway.mastercard.com/api/rest");
        request.getExtension().put("mid.version", "100");
        request.getExtension().put("midNo", "TESTDEVMER031");
        request.getExtension().put("mid.password", "metadata-password");
        Logger logger = (Logger) LoggerFactory.getLogger(MpgsApiClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            client.execute(request);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("event: CHANNEL_REQUEST_START", "payloadLength:", "payloadDigest:"))
                .anySatisfy(message -> assertThat(message)
                        .contains("event: CHANNEL_RESPONSE_END", "payloadLength:", "payloadDigest:"))
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain("\"sourceOfFunds\"", "\"card\"", "\"result\"", "\"response\""));
    }

    /**
     * MPGS RETRIEVE 查询必须使用 order.id 和 transaction.id 组成 URL，不能用平台 transactionId 或本地 requestId 替代。
     */
    @Test
    void shouldBuildQueryUrlFromChannelOrderNoAndChannelTransactionId() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        MpgsChannelProperties properties = new MpgsChannelProperties();
        properties.setBaseUrl("https://test-gateway.mastercard.com/api/rest");
        properties.setVersion("100");
        properties.setMerchantId("TESTDEVMER031");
        properties.setReadTimeoutMillis(30000);
        properties.setConnectTimeoutMillis(10000);
        MpgsApiClient client = new MpgsApiClient(properties, new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);
        ChannelPaymentRequest request = paymentRequest();
        request.setTransactionType(ChannelCapability.QUERY.getCode());
        request.setTransactionId("TX-PLATFORM-QUERY-001");
        request.setChannelOrderNo("ORDER-MPGS-QUERY-001");
        request.setChannelTransactionId("CH-MPGS-QUERY-001");
        request.getExtension().put("mid.password", "metadata-password");

        client.execute(request);

        assertThat(httpClient.lastRequest().method()).isEqualTo("GET");
        assertThat(httpClient.lastRequest().uri().toString())
                .contains("/order/ORDER-MPGS-QUERY-001/transaction/CH-MPGS-QUERY-001");
        assertThat(httpClient.lastRequest().uri().toString()).doesNotContain("TX-PLATFORM-QUERY-001");
    }

    @Test
    void shouldRetrieveAuthenticationUsingGetAndAuthenticationTransactionId() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        MpgsChannelProperties properties = new MpgsChannelProperties();
        MpgsApiClient client = new MpgsApiClient(
                properties, new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);
        MpgsThreeDsAuthenticationRequest request = new MpgsThreeDsAuthenticationRequest();
        request.setOperationId("OP-3DS-001");
        request.setTransactionId("TX-PLATFORM-3DS-001");
        request.setChannelOrderNo("ORDER-MPGS-3DS-001");
        request.setAuthenticationTransactionId("AUTH-MPGS-3DS-001");
        request.getExtension().put("requestUrl", "https://test-gateway.mastercard.com/api/rest");
        request.getExtension().put("mid.version", "100");
        request.getExtension().put("mid.merchantId", "TESTDEVMER031");
        request.getExtension().put("mid.password", "metadata-password");

        client.retrieveAuthentication(request);

        assertThat(httpClient.lastRequest().method()).isEqualTo("GET");
        assertThat(httpClient.lastRequest().uri().toString())
                .contains("/merchant/TESTDEVMER031/order/ORDER-MPGS-3DS-001/transaction/AUTH-MPGS-3DS-001");
        assertThat(httpClient.lastRequest().uri().toString()).doesNotContain("TX-PLATFORM-3DS-001");
    }

    @Test
    void shouldReadFinalAuthenticationStatusFromRetrieveTransaction() {
        String responseBody = "{\"result\":\"SUCCESS\","
                + "\"order\":{\"id\":\"ORDER-MPGS-3DS-001\",\"authenticationStatus\":\"AUTHENTICATION_SUCCESSFUL\"},"
                + "\"transaction\":{\"id\":\"AUTH-MPGS-3DS-001\",\"authenticationStatus\":\"AUTHENTICATION_SUCCESSFUL\"}}";
        CapturingHttpClient httpClient = new CapturingHttpClient(200, responseBody);
        MpgsApiClient client = new MpgsApiClient(
                new MpgsChannelProperties(), new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);

        MpgsThreeDsAuthenticationResponse response = client.retrieveAuthentication(authenticationRequest());

        assertThat(response.getAuthenticationStatus()).isEqualTo("AUTHENTICATION_SUCCESSFUL");
    }

    @Test
    void shouldRequireOrderAndTransactionIdForMpgsQueryReference() {
        MpgsPaymentChannelClient client = new MpgsPaymentChannelClient(null);
        ChannelQueryRequest requestIdOnly = new ChannelQueryRequest();
        requestIdOnly.setRequestId("CR-LOCAL-001");
        ChannelQueryRequest orderOnly = new ChannelQueryRequest();
        orderOnly.setChannelOrderNo("ORDER-MPGS-001");
        ChannelQueryRequest transactionOnly = new ChannelQueryRequest();
        transactionOnly.setChannelTransactionId("CH-MPGS-001");
        ChannelQueryRequest complete = new ChannelQueryRequest();
        complete.setChannelOrderNo("ORDER-MPGS-001");
        complete.setChannelTransactionId("CH-MPGS-001");

        assertThat(client.supportsQueryReference(requestIdOnly)).isFalse();
        assertThat(client.supportsQueryReference(orderOnly)).isFalse();
        assertThat(client.supportsQueryReference(transactionOnly)).isFalse();
        assertThat(client.supportsQueryReference(complete)).isTrue();
    }

    /** MPGS 已返回 HTTP 400 时结果是确定失败，不能进入等待渠道勾兑的 PROCESSING 状态。 */
    @Test
    void shouldTreatHttp400WithoutMpgsResultAsCertainFailure() {
        CapturingHttpClient httpClient = new CapturingHttpClient(400, "{}");
        MpgsChannelProperties properties = new MpgsChannelProperties();
        MpgsApiClient client = new MpgsApiClient(
                properties, new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);
        MpgsThreeDsAuthenticationRequest request = authenticationRequest();

        assertThatThrownBy(() -> client.initiateAuthentication(request))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isFalse());
    }

    /** MPGS 标准校验错误应保留字段和校验类型，便于定位请求模型问题且不能依赖原始响应落库。 */
    @Test
    void shouldRetainStructuredThreeDsValidationDiagnostics() {
        CapturingHttpClient httpClient = new CapturingHttpClient(400, """
                {
                  "result":"ERROR",
                  "error":{
                    "cause":"INVALID_REQUEST",
                    "explanation":"Invalid value provided for field sourceOfFunds.type",
                    "field":"sourceOfFunds.type",
                    "validationType":"INVALID"
                  }
                }
                """);
        MpgsApiClient client = new MpgsApiClient(new MpgsChannelProperties(),
                new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);

        MpgsThreeDsAuthenticationResponse response = client.initiateAuthentication(authenticationRequest());

        assertThat(response.getResponseCode()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getResponseMessage())
                .isEqualTo("Invalid value provided for field sourceOfFunds.type");
        assertThat(response.getExtension())
                .containsEntry("providerResult", "ERROR")
                .containsEntry("errorField", "sourceOfFunds.type")
                .containsEntry("validationType", "INVALID");
    }

    /** MPGS 5xx 不足以证明请求未处理，必须保留后续查询或回调勾兑语义。 */
    @Test
    void shouldKeepHttp500OutcomeUncertain() {
        CapturingHttpClient httpClient = new CapturingHttpClient(500, "{}");
        MpgsApiClient client = new MpgsApiClient(new MpgsChannelProperties(),
                new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);

        assertThatThrownBy(() -> client.initiateAuthentication(authenticationRequest()))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    /** HTTP 408 可能发生在渠道处理请求之后，不能按普通客户端参数错误直接失败。 */
    @Test
    void shouldKeepHttp408OutcomeUncertain() {
        CapturingHttpClient httpClient = new CapturingHttpClient(408, "{}");
        MpgsApiClient client = new MpgsApiClient(new MpgsChannelProperties(),
                new MpgsRequestMapper(), new MpgsResponseMapper(), httpClient);

        assertThatThrownBy(() -> client.initiateAuthentication(authenticationRequest()))
                .isInstanceOfSatisfying(ChannelResponseException.class,
                        exception -> assertThat(exception.isOutcomeUncertain()).isTrue());
    }

    private MpgsThreeDsAuthenticationRequest authenticationRequest() {
        MpgsThreeDsAuthenticationRequest request = new MpgsThreeDsAuthenticationRequest();
        request.setChannelCode("MPGS");
        request.setOperationId("OP-3DS-400");
        request.setTransactionId("TX-3DS-400");
        request.setChannelOrderNo("ORDER-3DS-400");
        request.setAuthenticationTransactionId("AUTH-3DS-400");
        request.setMerchantId("MERCHANT-001");
        request.setMerchantOrderNo("ORDER-001");
        request.setMerchantOrderId("ATTEMPT-001");
        request.setPaymentMethod("BANK_CARD");
        request.setAmount(new BigDecimal("42.13"));
        request.setCurrency("USD");
        request.setCardNo("3528000000000007");
        request.setExpirationMonth("01");
        request.setExpirationYear("2039");
        request.setSecurityCode("100");
        request.setCardBrand("JCB");
        request.setRedirectResponseUrl("https://checkout.example.test/3ds/return");
        request.setBrowserInfoJson("{\"userAgent\":\"test-agent\",\"acceptHeaders\":\"text/html\","
                + "\"challengeWindowSize\":\"FULL_SCREEN\",\"language\":\"en-US\"}");
        request.getExtension().put("requestUrl", "https://test-gateway.mastercard.com/api/rest");
        request.getExtension().put("mid.version", "100");
        request.getExtension().put("mid.merchantId", "TESTDEVMER031");
        request.getExtension().put("mid.password", "metadata-password");
        return request;
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

        private final int responseStatus;

        private final String responseBody;

        /**
         * authorization Header，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；高敏感字段，禁止明文打印日志，禁止写入异常消息。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String authorizationHeader;

        /**
         * last Request，用于保存 Capturing Http Client 中与 last请求 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private HttpRequest lastRequest;

        private CapturingHttpClient() {
            this(200, "{\"result\":\"SUCCESS\",\"response\":{\"gatewayCode\":\"APPROVED\","
                    + "\"gatewayRecommendation\":\"NO_ACTION\",\"acquirerCode\":\"00\","
                    + "\"acquirerMessage\":\"Approved\"},\"order\":{\"id\":\"202607170001\","
                    + "\"status\":\"CAPTURED\"},\"transaction\":{\"id\":\"CH202607170001\","
                    + "\"type\":\"PAYMENT\",\"authorizationCode\":\"123456\"}}");
        }

        private CapturingHttpClient(int responseStatus, String responseBody) {
            this.responseStatus = responseStatus;
            this.responseBody = responseBody;
        }

        private String authorizationHeader() {
            return authorizationHeader;
        }

        private HttpRequest lastRequest() {
            return lastRequest;
        }

        private String decodedAuthorization() {
            return new String(java.util.Base64.getDecoder().decode(authorizationHeader.substring("Basic ".length())));
        }

        /**
         * 不提供 Cookie 管理器，确保敏感信息脱敏测试不携带外部会话状态。
         */
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        /**
         * 返回固定连接超时，仅满足 JDK HTTP 客户端契约。
         */
        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(10));
        }

        /**
         * 禁止自动重定向，避免认证请求头被转发到其他地址。
         */
        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        /**
         * 不使用代理，保证测试不读取本机代理配置。
         */
        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        /**
         * 创建独立 TLS 上下文以满足抽象客户端契约，不建立真实网络连接。
         */
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

        /**
         * 返回默认 TLS 参数；当前测试不协商真实协议或密码套件。
         */
        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        /**
         * 不注册 JDK Authenticator，待测客户端必须自行构造 Basic 认证请求头。
         */
        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        /**
         * 固定声明 HTTP/1.1，使请求与响应协议版本保持确定。
         */
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        /**
         * 不提供异步执行器，因为脱敏测试只使用同步请求。
         */
        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            this.lastRequest = request;
            this.authorizationHeader = request.headers().firstValue("Authorization").orElse(null);
            @SuppressWarnings("unchecked")
            T body = (T) responseBody;
            return new SimpleHttpResponse<>(request, body, responseStatus);
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

    private record SimpleHttpResponse<T>(HttpRequest request, T body, int statusCode) implements HttpResponse<T> {

        /**
         * 返回空响应头，当前用例不依赖任何渠道响应头。
         */
        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        /**
         * 固定表示不存在重定向前响应。
         */
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        /**
         * 返回原测试请求 URI，使响应与被捕获请求保持关联。
         */
        @Override
        public URI uri() {
            return request.uri();
        }

        /**
         * 固定返回 HTTP/1.1，与测试客户端声明保持一致。
         */
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        /**
         * 规范化sslsession，返回当前业务步骤需要的业务值。
         * <p>
         * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
         * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
         * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
         * </p>
         * @return 方法执行后的业务结果、更新行数、转换对象或空结果
         */
        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}
