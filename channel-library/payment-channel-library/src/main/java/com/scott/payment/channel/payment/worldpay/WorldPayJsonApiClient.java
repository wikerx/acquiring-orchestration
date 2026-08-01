package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayJsonApiClient
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay JSON HTTP 客户端，位于 payment-channel-library 渠道实现层，负责读取后台 MID 三要素、构造可配置 WPGJSON endpoint、发送 Basic Auth HTTP 请求、记录脱敏请求响应日志，并把渠道响应转换为平台统一渠道结果。
 * @status : create
 */
@Slf4j
@Component
public class WorldPayJsonApiClient {

    /**
     * 请求 URL 扩展字段，由 service-payment 从渠道 MID 配置透传。
     */
    private static final String EXT_REQUEST_URL = "requestUrl";

    /**
     * 读取超时时间扩展字段，单位秒。
     */
    private static final String EXT_READ_TIMEOUT_SECONDS = "readTimeoutSeconds";

    /**
     * 原始请求 HTTP 方法审计 key。
     */
    private static final String RAW_HTTP_METHOD = "httpMethod";

    /**
     * 脱敏请求 URL 审计 key。
     */
    private static final String RAW_REQUEST_URL_MASKED = "requestUrlMasked";

    /**
     * 脱敏请求头审计 key。
     */
    private static final String RAW_REQUEST_HEADER_JSON_MASKED = "requestHeaderJsonMasked";

    /**
     * 脱敏请求体审计 key。
     */
    private static final String RAW_REQUEST_BODY_JSON_MASKED = "requestBodyJsonMasked";

    /**
     * 脱敏响应头审计 key。
     */
    private static final String RAW_RESPONSE_HEADER_JSON_MASKED = "responseHeaderJsonMasked";

    /**
     * 脱敏响应体审计 key。
     */
    private static final String RAW_RESPONSE_BODY_JSON_MASKED = "responseBodyJsonMasked";

    /**
     * WPGJSON 默认 HTTP 方法；交易动作默认使用 JSON POST，查询默认使用 GET。
     */
    private static final String HTTP_METHOD_POST = "POST";

    /**
     * WPGJSON 查询 HTTP 方法，除非配置 queryHttpMethod=POST，否则查询使用 GET。
     */
    private static final String HTTP_METHOD_GET = "GET";

    /**
     * Access Worldpay Payments API 默认媒体类型；可通过 MID 元数据 contentType 覆盖。
     */
    private static final String DEFAULT_CONTENT_TYPE = "application/vnd.worldpay.payments-v7+json";

    /**
     * Access Worldpay Payments API 默认响应媒体类型；可通过 MID 元数据 accept 覆盖。
     */
    private static final String DEFAULT_ACCEPT = "application/vnd.worldpay.payments-v7+json";

    /**
     * Access Worldpay Card Payments v7 媒体类型；选择 CARD_PAYMENTS API 族时使用。
     */
    private static final String CARD_PAYMENTS_CONTENT_TYPE = "application/vnd.worldpay.cardPayments-v7+json";

    /**
     * WPGJSON 首笔交易默认路径，按 Access Worldpay Payments API 使用 /api/payments。
     */
    private static final String DEFAULT_PAYMENT_PATH = "/api/payments";

    /**
     * Access Worldpay Card Payments v7 首笔交易默认路径。
     */
    private static final String CARD_PAYMENTS_PAYMENT_PATH = "/api/cardPayments/customerInitiatedTransactions";

    /**
     * WPGJSON 查询默认路径，按 transactionReference 和 merchant entity 查询事件。
     */
    private static final String DEFAULT_QUERY_PATH = "/api/payments/events?transactionRef={transactionReference}&entity={merchantCode}";

    /**
     * 完整 PAN 字段 cardNumber 脱敏规则，保留前 6 后 4。
     */
    private static final Pattern WORLDPAY_CARD_NUMBER_PATTERN = Pattern.compile(
            "(\"cardNumber\"\\s*:\\s*\")([0-9]{6})([0-9]{1,19})([0-9]{4})(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * WorldPay 凭据和认证敏感字段脱敏规则。
     */
    private static final Pattern WORLDPAY_SECRET_FIELD_PATTERN = Pattern.compile(
            "(\"(?:cvc|cavv|authenticationValue|password|apiPassword|basicAuthPassword|interfacePassword|authorization|Authorization)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * WorldPay JSON 个人信息字段脱敏规则，避免持卡人姓名和账单地址完整进入渠道日志。
     */
    private static final Pattern WORLDPAY_PERSONAL_FIELD_PATTERN = Pattern.compile(
            "(\"(?:cardHolderName|address1|postalCode|email|phone)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 平台统一渠道请求到 WPGJSON 请求体的映射器。
     */
    private final WorldPayJsonRequestMapper requestMapper;

    /**
     * WPGJSON 原始响应到平台统一渠道响应的映射器。
     */
    private final WorldPayJsonResponseMapper responseMapper;

    /**
     * JDK HTTP 客户端，测试环境可注入替身以断言请求头、URL 和请求体。
     */
    private final HttpClient httpClient;

    /**
     * 创建 WorldPay JSON API 客户端。
     *
     * @param requestMapper 请求映射器
     * @param responseMapper 响应映射器
     */
    @Autowired
    public WorldPayJsonApiClient(WorldPayJsonRequestMapper requestMapper,
                                 WorldPayJsonResponseMapper responseMapper) {
        this(requestMapper, responseMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    /**
     * 创建 WorldPay JSON API 客户端。
     *
     * @param requestMapper 请求映射器
     * @param responseMapper 响应映射器
     * @param httpClient HTTP 客户端，测试可注入替身
     */
    WorldPayJsonApiClient(WorldPayJsonRequestMapper requestMapper,
                          WorldPayJsonResponseMapper responseMapper,
                          HttpClient httpClient) {
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.httpClient = httpClient;
    }

    /**
     * 执行 WPGJSON 渠道请求。
     * <p>
     * 当前实现只建设 WPGJSON HTTP 调用链路：从 MID 元数据读取 merchantCode、Basic Auth 用户名和密码，构造可配置 endpoint，
     * 发送脱敏审计可追踪的 JSON 请求，并把渠道原始状态映射为统一渠道响应。完整 PAN、CVV、CAVV、密码和 Authorization 头不得进入日志或审计字段。
     * </p>
     * @param request 平台统一渠道请求
     * @return 平台统一渠道响应
     */
    public ChannelPaymentResponse execute(ChannelPaymentRequest request) {
        validateRequest(request);
        WorldPayJsonMidConfig midConfig = resolveMidConfig(request);
        long startNanos = System.nanoTime();
        String httpMethod = httpMethod(request);
        String operation = operation(request);
        String url = null;
        String requestBody = null;
        try {
            url = buildUrl(request, midConfig);
            HttpResponse<String> response;
            if (HTTP_METHOD_GET.equals(httpMethod)) {
                logRequest(request, httpMethod, operation, url, null, midConfig);
                fillRawRequestAudit(request, httpMethod, url, null);
                response = sendGet(request, url, midConfig);
            } else {
                WorldPayJsonRequestPayload payload = requestMapper.toWorldPayRequest(request, midConfig.merchantCode());
                requestBody = JsonUtils.toJsonString(payload);
                logRequest(request, httpMethod, operation, url, payload, midConfig);
                fillRawRequestAudit(request, httpMethod, url, requestBody);
                response = sendPost(request, url, requestBody, midConfig);
            }
            return handleResponse(request, response, httpMethod, operation, url, startNanos);
        } catch (java.net.http.HttpTimeoutException exception) {
            logRequestException(request, httpMethod, operation, url, midConfig, startNanos, exception);
            throw new ChannelTimeoutException("WorldPay JSON request timed out", exception);
        } catch (IOException exception) {
            logRequestException(request, httpMethod, operation, url, midConfig, startNanos, exception);
            throw new ChannelRequestException("WorldPay JSON network request failed", exception, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logRequestException(request, httpMethod, operation, url, midConfig, startNanos, exception);
            throw new ChannelRequestException("WorldPay JSON request was interrupted", exception, true);
        }
    }

    /**
     * 处理 WPGJSON HTTP 响应并填充渠道审计字段。
     *
     * @param request 平台统一渠道请求
     * @param response HTTP 响应
     * @param httpMethod HTTP 方法
     * @param operation WPGJSON 操作
     * @param requestUrl 请求 URL
     * @param startNanos 请求开始时间
     * @return 平台统一渠道响应
     */
    private ChannelPaymentResponse handleResponse(ChannelPaymentRequest request,
                                                  HttpResponse<String> response,
                                                  String httpMethod,
                                                  String operation,
                                                  String requestUrl,
                                                  long startNanos) {
        String body = response.body();
        log.info("event: CHANNEL_RESPONSE_END traceId: {} channelCode: {} apiOperation: {} endpointHost: {} endpointPath: {} httpMethod: {} midSummary: {} transactionId: {} operationId: {} channelRequestId: {} httpStatus: {} responseSummary: {} channelResult: {} acquirerCode: {} responseCode: {} stan: {} channelTransactionId: {} durationMs: {}",
                TraceContext.getTraceId(),
                request.getChannelCode(),
                operation,
                host(requestUrl),
                path(requestUrl),
                httpMethod,
                midSummary(merchantCode(request)),
                request.getTransactionId(),
                request.getOperationId(),
                requestId(request),
                response.statusCode(),
                JsonUtils.toJsonString(toMaskedJsonLogObject(body)),
                rawStatus(body),
                rawAcquirerCode(body),
                rawResponseCode(body),
                rawStan(body),
                rawChannelTransactionId(body),
                elapsedMillis(startNanos));
        if (!StringUtils.hasText(body)) {
            throw new ChannelResponseException("WorldPay JSON response body is empty");
        }
        WorldPayJsonResponsePayload payload = parseResponseBody(body, response.statusCode());
        if ((response.statusCode() < 200 || response.statusCode() >= 300) && !hasWorldPayBusinessResult(payload)) {
            throw new ChannelResponseException("WorldPay JSON HTTP response is not successful, status: " + response.statusCode());
        }
        ChannelPaymentResponse channelResponse = responseMapper.toChannelResponse(request, payload);
        fillRawResponseAudit(channelResponse, request, response, httpMethod, requestUrl);
        return channelResponse;
    }

    /**
     * 使用 POST 调用 WPGJSON 交易接口。
     *
     * @param request 平台统一渠道请求
     * @param url 请求 URL
     * @param requestBody JSON 请求体
     * @param midConfig MID 配置
     * @return HTTP 响应
     * @throws IOException 网络异常
     * @throws InterruptedException 当前线程被中断
     */
    private HttpResponse<String> sendPost(ChannelPaymentRequest request,
                                          String url,
                                          String requestBody,
                                          WorldPayJsonMidConfig midConfig) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout(request))
                .header("Authorization", basicAuthHeader(midConfig))
                .header("Content-Type", contentType(request))
                .header("Accept", accept(request))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 使用 GET 调用 WPGJSON 查询接口，适用于用户在 MID 元数据中明确配置 queryHttpMethod=GET 的场景。
     *
     * @param request 平台统一渠道请求
     * @param url 请求 URL
     * @param midConfig MID 配置
     * @return HTTP 响应
     * @throws IOException 网络异常
     * @throws InterruptedException 当前线程被中断
     */
    private HttpResponse<String> sendGet(ChannelPaymentRequest request,
                                         String url,
                                         WorldPayJsonMidConfig midConfig) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout(request))
                .header("Authorization", basicAuthHeader(midConfig))
                .header("Accept", accept(request))
                .GET()
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 解析 WPGJSON 响应体。
     *
     * @param body HTTP 响应体
     * @param httpStatus HTTP 状态码
     * @return WPGJSON 响应对象
     */
    private WorldPayJsonResponsePayload parseResponseBody(String body, int httpStatus) {
        try {
            return JsonUtils.parseObject(body, WorldPayJsonResponsePayload.class);
        } catch (RuntimeException exception) {
            if (httpStatus < 200 || httpStatus >= 300) {
                throw new ChannelResponseException("WorldPay JSON HTTP response is not successful, status: " + httpStatus, exception);
            }
            throw new ChannelResponseException("WorldPay JSON response parse failed", exception);
        }
    }

    /**
     * 判断 HTTP 非 2xx 响应是否仍包含可映射的 WorldPay 业务结果。
     *
     * @param payload WPGJSON 响应对象
     * @return true 表示可按渠道失败结果映射
     */
    private boolean hasWorldPayBusinessResult(WorldPayJsonResponsePayload payload) {
        return payload != null
                && (StringUtils.hasText(payload.getOutcome())
                || StringUtils.hasText(payload.getStatus())
                || StringUtils.hasText(payload.getResultCode())
                || payload.getError() != null);
    }

    /**
     * 构建 WPGJSON 请求 URL。
     * <p>
     * 默认以后台 requestUrl 作为 base URL，首笔交易使用 metadata.endpointPath 或默认 /api/payments，查询使用 metadata.queryPath。
     * Worldpay 请款、退款、撤销依赖上次响应 action link，当前支持 extension 或 MID 元数据显式传入链接/路径，不伪造 span 或渠道动作 URL。
     * </p>
     * @param request 平台统一渠道请求
     * @param midConfig MID 配置
     * @return 请求 URL
     */
    String buildUrl(ChannelPaymentRequest request, WorldPayJsonMidConfig midConfig) {
        String baseUrl = requiredText(firstText(extensionValue(request, EXT_REQUEST_URL), extensionValue(request, "mid.baseUrl")),
                "WorldPay JSON requestUrl is required");
        String transactionType = normalizeType(request.getTransactionType());
        if (ChannelCapability.QUERY.getCode().equals(transactionType)) {
            String queryPath = firstText(worldpayActionLink(request, transactionType), extensionValue(request, "mid.queryPath"), DEFAULT_QUERY_PATH);
            return appendPath(baseUrl, expandPath(queryPath, request, midConfig));
        }
        if (isFollowUp(transactionType)) {
            String actionPath = requiredText(firstText(worldpayActionLink(request, transactionType), configuredFollowUpPath(request, transactionType)),
                    "WorldPay JSON follow-up action link is required");
            return appendPath(baseUrl, expandPath(actionPath, request, midConfig));
        }
        String endpointPath = firstText(extensionValue(request, "mid.endpointPath"), extensionValue(request, "mid.paymentPath"),
                isCardPaymentsApi(request) ? CARD_PAYMENTS_PAYMENT_PATH : DEFAULT_PAYMENT_PATH);
        return appendPath(baseUrl, expandPath(endpointPath, request, midConfig));
    }

    /**
     * 判断是否显式选择 Access Worldpay Card Payments v7 API 族。
     *
     * @param request 平台统一渠道请求
     * @return true 表示使用 Card Payments v7 默认路径和媒体类型
     */
    private boolean isCardPaymentsApi(ChannelPaymentRequest request) {
        String apiFamily = firstText(extensionValue(request, "mid.apiFamily"), extensionValue(request, "apiFamily"));
        if ("CARD_PAYMENTS".equalsIgnoreCase(apiFamily)) {
            return true;
        }
        String endpointPath = firstText(extensionValue(request, "mid.endpointPath"), extensionValue(request, "mid.paymentPath"));
        return StringUtils.hasText(endpointPath) && endpointPath.toLowerCase(Locale.ROOT).contains("cardpayments");
    }

    /**
     * 解析本次请求 HTTP 方法。
     *
     * @param request 平台统一渠道请求
     * @return HTTP 方法
     */
    private String httpMethod(ChannelPaymentRequest request) {
        if (ChannelCapability.QUERY.getCode().equals(normalizeType(request.getTransactionType()))) {
            return "POST".equalsIgnoreCase(extensionValue(request, "mid.queryHttpMethod"))
                    ? HTTP_METHOD_POST
                    : HTTP_METHOD_GET;
        }
        return HTTP_METHOD_POST;
    }

    /**
     * 解析 WPGJSON 操作类型。
     *
     * @param request 平台统一渠道请求
     * @return 操作类型
     */
    private String operation(ChannelPaymentRequest request) {
        String transactionType = normalizeType(request.getTransactionType());
        if (ChannelCapability.PAYMENT.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.PAYMENT;
        }
        if (ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.AUTHORIZE;
        }
        if (ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.PRE_AUTHORIZE;
        }
        if (ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.CAPTURE;
        }
        if (ChannelCapability.REFUND.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.REFUND;
        }
        if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.VOID;
        }
        if (ChannelCapability.QUERY.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.QUERY;
        }
        throw new ChannelRequestException("WorldPay JSON unsupported transaction type: " + transactionType);
    }

    /**
     * 展开 endpoint 路径模板。
     *
     * @param pathTemplate 路径模板
     * @param request 平台统一渠道请求
     * @param midConfig MID 配置
     * @return 展开后的路径
     */
    private String expandPath(String pathTemplate, ChannelPaymentRequest request, WorldPayJsonMidConfig midConfig) {
        String path = firstText(pathTemplate, "");
        return path
                .replace("{merchantCode}", encode(midConfig.merchantCode()))
                .replace("{orderCode}", encode(request.getChannelOrderNo()))
                .replace("{transactionReference}", encode(firstText(request.getChannelTransactionId(), requestId(request), request.getTransactionId())))
                .replace("{paymentId}", encode(firstText(
                        extensionValue(request, "targetPaymentId"),
                        extensionValue(request, "sourcePaymentId"),
                        extensionValue(request, "targetTransactionId"),
                        request.getChannelTransactionId(),
                        request.getTransactionId())))
                .replace("{transactionId}", encode(firstText(request.getChannelTransactionId(), request.getTransactionId())))
                .replace("{requestId}", encode(firstText(requestId(request), "")));
    }

    /**
     * 判断是否为 Worldpay 后续动作。
     *
     * @param transactionType 平台交易类型
     * @return true 表示请款、退款、撤销或冲正
     */
    private boolean isFollowUp(String transactionType) {
        return ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)
                || ChannelCapability.REFUND.getCode().equals(transactionType)
                || ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType);
    }

    /**
     * 读取后续动作的 Worldpay action link。
     *
     * @param request 平台统一渠道请求
     * @param transactionType 平台交易类型
     * @return action href 或 path
     */
    private String worldpayActionLink(ChannelPaymentRequest request, String transactionType) {
        if (ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            return firstText(extensionValue(request, "worldpaySettleLink"), extensionValue(request, "worldpayCaptureLink"),
                    extensionValue(request, "targetActionLink"));
        }
        if (ChannelCapability.REFUND.getCode().equals(transactionType)) {
            return firstText(extensionValue(request, "worldpayRefundLink"), extensionValue(request, "targetActionLink"));
        }
        if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            return firstText(extensionValue(request, "worldpayCancelLink"), extensionValue(request, "worldpayVoidLink"),
                    extensionValue(request, "targetActionLink"));
        }
        if (ChannelCapability.QUERY.getCode().equals(transactionType)) {
            return firstText(extensionValue(request, "worldpayEventsLink"), extensionValue(request, "worldpayQueryLink"),
                    extensionValue(request, "targetActionLink"));
        }
        return null;
    }

    /**
     * 读取 MID 元数据配置的后续动作路径。
     *
     * @param request 平台统一渠道请求
     * @param transactionType 平台交易类型
     * @return 后续动作 path
     */
    private String configuredFollowUpPath(ChannelPaymentRequest request, String transactionType) {
        if (ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            return firstText(extensionValue(request, "mid.capturePath"), extensionValue(request, "mid.settlePath"));
        }
        if (ChannelCapability.REFUND.getCode().equals(transactionType)) {
            return extensionValue(request, "mid.refundPath");
        }
        if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            return firstText(extensionValue(request, "mid.voidPath"), extensionValue(request, "mid.cancelPath"));
        }
        return null;
    }

    /**
     * 追加 base URL 与 path，避免双斜杠。
     *
     * @param baseUrl 基础 URL
     * @param path endpoint path
     * @return 完整 URL
     */
    private String appendPath(String baseUrl, String path) {
        if (!StringUtils.hasText(path)) {
            return baseUrl;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String left = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String right = path.startsWith("/") ? path : "/" + path;
        return left + right;
    }

    /**
     * 读取并校验 WorldPay MID 三要素。
     *
     * @param request 平台统一渠道请求
     * @return MID 配置
     */
    private WorldPayJsonMidConfig resolveMidConfig(ChannelPaymentRequest request) {
        String merchantCode = requiredText(firstText(
                extensionValue(request, "mid.worldpayMerchantCode"),
                extensionValue(request, "mid.merchantCode"),
                extensionValue(request, "mid.channelMid"),
                extensionValue(request, "mid.merchantId"),
                extensionValue(request, "mid.merchantNo"),
                extensionValue(request, "mid.midNo"),
                extensionValue(request, "midNo")
        ), "WorldPay JSON merchantCode is required");
        String username = requiredText(firstText(
                extensionValue(request, "mid.basicAuthUsername"),
                extensionValue(request, "mid.basicAuthenticationUsername"),
                extensionValue(request, "mid.username"),
                extensionValue(request, "mid.apiUsername"),
                extensionValue(request, "mid.userName")
        ), "WorldPay JSON Basic Auth username is required");
        String password = requiredText(firstText(
                extensionValue(request, "mid.basicAuthPassword"),
                extensionValue(request, "mid.basicAuthenticationPassword"),
                extensionValue(request, "mid.interfacePassword"),
                extensionValue(request, "mid.password"),
                extensionValue(request, "mid.apiPassword")
        ), "WorldPay JSON Basic Auth password is required");
        return new WorldPayJsonMidConfig(merchantCode, username, password);
    }

    /**
     * 构造 Basic Auth 请求头，该值只用于 HTTP 请求，禁止写入日志。
     *
     * @param midConfig MID 配置
     * @return Basic Auth 请求头
     */
    private String basicAuthHeader(WorldPayJsonMidConfig midConfig) {
        String raw = midConfig.username() + ":" + midConfig.password();
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验渠道请求基础字段。
     *
     * @param request 平台统一渠道请求
     */
    private void validateRequest(ChannelPaymentRequest request) {
        if (request == null) {
            throw new ChannelRequestException("WorldPay JSON request is required");
        }
        requiredText(request.getTransactionType(), "WorldPay JSON transactionType is required");
        requiredText(request.getChannelOrderNo(), "WorldPay JSON channelOrderNo is required");
    }

    /**
     * 记录 WPGJSON 脱敏请求日志。
     *
     * @param request 平台统一渠道请求
     * @param httpMethod HTTP 方法
     * @param operation WPGJSON 操作
     * @param url 请求 URL
     * @param payload 请求载荷，GET 查询为空
     * @param midConfig MID 配置
     */
    private void logRequest(ChannelPaymentRequest request,
                            String httpMethod,
                            String operation,
                            String url,
                            WorldPayJsonRequestPayload payload,
                            WorldPayJsonMidConfig midConfig) {
        log.info("event: CHANNEL_REQUEST_START traceId: {} channelCode: {} apiOperation: {} endpointHost: {} endpointPath: {} httpMethod: {} midSummary: {} transactionId: {} operationId: {} channelRequestId: {} channelTransactionId: {} requestSummary: {}",
                TraceContext.getTraceId(),
                request.getChannelCode(),
                operation,
                host(url),
                path(url),
                httpMethod,
                midSummary(midConfig.merchantCode()),
                request.getTransactionId(),
                request.getOperationId(),
                requestId(request),
                request.getChannelTransactionId(),
                JsonUtils.toJsonString(toMaskedJsonLogObject(payload)));
    }

    /**
     * 记录 WPGJSON 请求异常日志。
     *
     * @param request 平台统一渠道请求
     * @param httpMethod HTTP 方法
     * @param operation WPGJSON 操作
     * @param url 请求 URL
     * @param midConfig MID 配置
     * @param startNanos 请求开始时间
     * @param exception 原始异常
     */
    private void logRequestException(ChannelPaymentRequest request,
                                     String httpMethod,
                                     String operation,
                                     String url,
                                     WorldPayJsonMidConfig midConfig,
                                     long startNanos,
                                     Exception exception) {
        log.warn("event: CHANNEL_REQUEST_FAILED traceId: {} channelCode: {} apiOperation: {} endpointHost: {} endpointPath: {} httpMethod: {} midSummary: {} transactionId: {} operationId: {} channelRequestId: {} channelTransactionId: {} durationMs: {} exceptionType: {}",
                TraceContext.getTraceId(),
                request == null ? null : request.getChannelCode(),
                operation,
                host(url),
                path(url),
                httpMethod,
                midConfig == null ? null : midSummary(midConfig.merchantCode()),
                request == null ? null : request.getTransactionId(),
                request == null ? null : request.getOperationId(),
                requestId(request),
                request == null ? null : request.getChannelTransactionId(),
                elapsedMillis(startNanos),
                exception.getClass().getSimpleName(),
                exception);
    }

    /**
     * 填充原始请求审计字段，所有敏感值必须先脱敏。
     *
     * @param request 平台统一渠道请求
     * @param httpMethod HTTP 方法
     * @param requestUrl 请求 URL
     * @param requestBody 请求体
     */
    private void fillRawRequestAudit(ChannelPaymentRequest request, String httpMethod, String requestUrl, String requestBody) {
        if (request == null) {
            return;
        }
        request.getExtension().put(RAW_HTTP_METHOD, httpMethod);
        request.getExtension().put(RAW_REQUEST_URL_MASKED, requestUrl);
        request.getExtension().put(RAW_REQUEST_HEADER_JSON_MASKED, JsonUtils.toJsonString(Collections.singletonMap("Authorization", "Basic ***")));
        request.getExtension().put(RAW_REQUEST_BODY_JSON_MASKED, StringUtils.hasText(requestBody)
                ? maskWorldPayJson(requestBody)
                : JsonUtils.toJsonString(Collections.emptyMap()));
    }

    /**
     * 填充原始响应审计字段，供 service-payment 写入渠道请求表。
     *
     * @param channelResponse 平台统一渠道响应
     * @param request 平台统一渠道请求
     * @param response HTTP 响应
     * @param httpMethod HTTP 方法
     * @param requestUrl 请求 URL
     */
    private void fillRawResponseAudit(ChannelPaymentResponse channelResponse,
                                      ChannelPaymentRequest request,
                                      HttpResponse<String> response,
                                      String httpMethod,
                                      String requestUrl) {
        if (channelResponse == null || response == null) {
            return;
        }
        channelResponse.setHttpStatus(response.statusCode());
        channelResponse.setHttpMethod(firstText(httpMethod, auditValue(request, RAW_HTTP_METHOD)));
        channelResponse.setRequestUrlMasked(firstText(requestUrl, auditValue(request, RAW_REQUEST_URL_MASKED)));
        channelResponse.setRequestHeaderJsonMasked(auditValue(request, RAW_REQUEST_HEADER_JSON_MASKED));
        channelResponse.setRequestBodyJsonMasked(auditValue(request, RAW_REQUEST_BODY_JSON_MASKED));
        channelResponse.setResponseHeaderJsonMasked(responseHeadersMasked(response));
        channelResponse.setResponseBodyJsonMasked(maskWorldPayJson(response.body()));
        channelResponse.getRawResponse().put("httpStatus", String.valueOf(response.statusCode()));
        response.headers().firstValue("WP-CorrelationId")
                .ifPresent(value -> channelResponse.getRawResponse().put("wpCorrelationId", value));
        putIfText(channelResponse, RAW_HTTP_METHOD, channelResponse.getHttpMethod());
        putIfText(channelResponse, RAW_REQUEST_URL_MASKED, channelResponse.getRequestUrlMasked());
        putIfText(channelResponse, RAW_REQUEST_HEADER_JSON_MASKED, channelResponse.getRequestHeaderJsonMasked());
        putIfText(channelResponse, RAW_REQUEST_BODY_JSON_MASKED, channelResponse.getRequestBodyJsonMasked());
        putIfText(channelResponse, RAW_RESPONSE_HEADER_JSON_MASKED, channelResponse.getResponseHeaderJsonMasked());
        putIfText(channelResponse, RAW_RESPONSE_BODY_JSON_MASKED, channelResponse.getResponseBodyJsonMasked());
    }

    /**
     * 写入非空渠道扩展响应字段。
     *
     * @param channelResponse 平台统一渠道响应
     * @param key 扩展 key
     * @param value 扩展值
     */
    private void putIfText(ChannelPaymentResponse channelResponse, String key, String value) {
        if (channelResponse != null && StringUtils.hasText(value)) {
            channelResponse.getRawResponse().put(key, value);
        }
    }

    /**
     * 读取请求审计字段。
     *
     * @param request 平台统一渠道请求
     * @param key 扩展 key
     * @return 审计字段值
     */
    private String auditValue(ChannelPaymentRequest request, String key) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get(key);
    }

    /**
     * 读取渠道请求号。
     *
     * @param request 平台统一渠道请求
     * @return 渠道请求号
     */
    private String requestId(ChannelPaymentRequest request) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get("requestId");
    }

    /**
     * 读取渠道 MID 商户代码。
     *
     * @param request 平台统一渠道请求
     * @return MID 商户代码
     */
    private String merchantCode(ChannelPaymentRequest request) {
        return firstText(
                extensionValue(request, "mid.worldpayMerchantCode"),
                extensionValue(request, "mid.merchantCode"),
                extensionValue(request, "mid.channelMid"),
                extensionValue(request, "mid.merchantId"),
                extensionValue(request, "mid.merchantNo"),
                extensionValue(request, "mid.midNo"),
                extensionValue(request, "midNo")
        );
    }

    /**
     * 生成 MID 摘要，日志只保留首尾少量字符。
     *
     * @param merchantCode WorldPay 商户代码
     * @return 脱敏 MID 摘要
     */
    private String midSummary(String merchantCode) {
        if (!StringUtils.hasText(merchantCode)) {
            return null;
        }
        String normalized = merchantCode.trim();
        if (normalized.length() <= 6) {
            return "***";
        }
        return normalized.substring(0, 3) + "***" + normalized.substring(normalized.length() - 3);
    }

    /**
     * 读取渠道扩展字段。
     *
     * @param request 平台统一渠道请求
     * @param key 扩展 key
     * @return 扩展字段值
     */
    private String extensionValue(ChannelPaymentRequest request, String key) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get(key);
    }

    /**
     * 读取请求超时时间。
     *
     * @param request 平台统一渠道请求
     * @return 请求超时时间
     */
    private Duration readTimeout(ChannelPaymentRequest request) {
        String configuredSeconds = extensionValue(request, EXT_READ_TIMEOUT_SECONDS);
        if (StringUtils.hasText(configuredSeconds)) {
            try {
                return Duration.ofSeconds(Long.parseLong(configuredSeconds));
            } catch (NumberFormatException ignored) {
                return Duration.ofSeconds(30);
            }
        }
        return Duration.ofSeconds(30);
    }

    /**
     * 读取请求 Content-Type，默认使用 Access Worldpay Payments API 媒体类型。
     *
     * @param request 平台统一渠道请求
     * @return HTTP Content-Type
     */
    private String contentType(ChannelPaymentRequest request) {
        return firstText(extensionValue(request, "mid.contentType"), extensionValue(request, "mid.mediaType"),
                isCardPaymentsApi(request) ? CARD_PAYMENTS_CONTENT_TYPE : DEFAULT_CONTENT_TYPE);
    }

    /**
     * 读取请求 Accept，默认使用 Access Worldpay Payments API 媒体类型。
     *
     * @param request 平台统一渠道请求
     * @return HTTP Accept
     */
    private String accept(ChannelPaymentRequest request) {
        return firstText(extensionValue(request, "mid.accept"), extensionValue(request, "mid.mediaType"),
                isCardPaymentsApi(request) ? CARD_PAYMENTS_CONTENT_TYPE : DEFAULT_ACCEPT);
    }

    /**
     * 生成脱敏响应头 JSON，只保留排查所需的受控头。
     *
     * @param response HTTP 响应
     * @return 脱敏响应头 JSON
     */
    private String responseHeadersMasked(HttpResponse<String> response) {
        if (response == null || response.headers() == null) {
            return JsonUtils.toJsonString(Collections.emptyMap());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().firstValue("WP-CorrelationId").ifPresent(value -> headers.put("WP-CorrelationId", value));
        response.headers().firstValue("Content-Type").ifPresent(value -> headers.put("Content-Type", value));
        return JsonUtils.toJsonString(headers);
    }

    /**
     * 计算请求耗时。
     *
     * @param startNanos 请求开始时间
     * @return 毫秒耗时
     */
    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /**
     * 提取 endpoint 主机名。
     *
     * @param url 请求 URL
     * @return 主机名
     */
    private String host(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return URI.create(url).getHost();
    }

    /**
     * 提取 endpoint path。
     *
     * @param url 请求 URL
     * @return path
     */
    private String path(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return URI.create(url).getPath();
    }

    /**
     * URL 编码路径变量。
     *
     * @param value 原始路径变量
     * @return 编码后的路径变量
     */
    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * 标准化交易类型。
     *
     * @param transactionType 平台交易类型
     * @return 大写交易类型
     */
    private String normalizeType(String transactionType) {
        return requiredText(transactionType, "WorldPay JSON transactionType is required").toUpperCase(Locale.ROOT);
    }

    /**
     * 校验并返回非空文本。
     *
     * @param value 待校验文本
     * @param message 缺失时抛出的错误消息
     * @return 去除首尾空白后的文本
     */
    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ChannelRequestException(message);
        }
        return value.trim();
    }

    /**
     * 返回首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 将请求对象转为可直接复制排查的脱敏 JSON 日志对象。
     *
     * @param payload WPGJSON 请求体
     * @return 可序列化的脱敏对象
     */
    private Object toMaskedJsonLogObject(WorldPayJsonRequestPayload payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        return toMaskedJsonLogObject(JsonUtils.toJsonString(payload));
    }

    /**
     * 将 JSON 字符串转为可直接复制排查的脱敏 JSON 日志对象。
     *
     * @param json 原始 JSON
     * @return 可序列化的脱敏对象；非 JSON 内容返回脱敏字符串
     */
    private Object toMaskedJsonLogObject(String json) {
        String masked = maskWorldPayJson(json);
        if (!StringUtils.hasText(masked)) {
            return Collections.emptyMap();
        }
        try {
            return JsonUtils.parseObject(masked, Object.class);
        } catch (RuntimeException exception) {
            return masked;
        }
    }

    /**
     * 对 WPGJSON 请求/响应执行脱敏。
     * <p>
     * 先复用全局 JSON 脱敏工具，再补充 WorldPay JSON 中 cardNumber、cvc、cavv 和 Basic Auth 相关字段，确保日志、测试和审计字段使用同一套规则。
     * </p>
     * @param json 原始 JSON
     * @return 脱敏后的 JSON
     */
    static String maskWorldPayJson(String json) {
        String masked = SensitiveDataMaskUtils.maskJsonSafely(json);
        if (masked == null || masked.isEmpty()) {
            return masked;
        }
        masked = WORLDPAY_CARD_NUMBER_PATTERN.matcher(masked).replaceAll(matchResult -> Matcher.quoteReplacement(
                matchResult.group(1)
                        + matchResult.group(2)
                        + "******"
                        + matchResult.group(4)
                        + matchResult.group(5)
        ));
        masked = WORLDPAY_SECRET_FIELD_PATTERN.matcher(masked).replaceAll("$1***$3");
        return WORLDPAY_PERSONAL_FIELD_PATTERN.matcher(masked).replaceAll("$1***$3");
    }

    /**
     * 从原始 JSON 中提取状态用于日志摘要。
     *
     * @param body 响应体
     * @return 渠道状态
     */
    private String rawStatus(String body) {
        WorldPayJsonResponsePayload payload = tryParse(body);
        return payload == null ? null : firstText(payload.getStatus(), payload.getOutcome());
    }

    /**
     * 从原始 JSON 中提取收单响应码用于日志摘要。
     *
     * @param body 响应体
     * @return 收单响应码
     */
    private String rawAcquirerCode(String body) {
        WorldPayJsonResponsePayload payload = tryParse(body);
        return payload == null ? null : firstText(payload.getAcquirerCode(),
                payload.getIssuer() == null ? null : payload.getIssuer().getResponseCode());
    }

    /**
     * 从原始 JSON 中提取响应码用于日志摘要。
     *
     * @param body 响应体
     * @return 响应码
     */
    private String rawResponseCode(String body) {
        WorldPayJsonResponsePayload payload = tryParse(body);
        return payload == null ? null : firstText(payload.getResponseCode(),
                payload.getIssuer() == null ? null : payload.getIssuer().getResponseCode(),
                payload.getRefusalCode(), payload.getResultCode(), payload.getOutcome());
    }

    /**
     * 从原始 JSON 中提取 STAN 用于日志摘要。
     *
     * @param body 响应体
     * @return STAN
     */
    private String rawStan(String body) {
        WorldPayJsonResponsePayload payload = tryParse(body);
        return payload == null ? null : firstText(payload.getStan(),
                payload.getIssuer() == null ? null : payload.getIssuer().getStan());
    }

    /**
     * 从原始 JSON 中提取渠道交易号用于日志摘要。
     *
     * @param body 响应体
     * @return 渠道交易号
     */
    private String rawChannelTransactionId(String body) {
        WorldPayJsonResponsePayload payload = tryParse(body);
        return payload == null ? null : firstText(payload.getPaymentId(), payload.getTransactionId());
    }

    /**
     * 尝试解析响应体，解析失败时返回 null，避免日志摘要影响主流程错误处理。
     *
     * @param body 响应体
     * @return 响应对象
     */
    private WorldPayJsonResponsePayload tryParse(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(body, WorldPayJsonResponsePayload.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * WorldPay JSON MID 三要素，密码只允许用于构造 Basic Auth 头。
     *
     * @param merchantCode WorldPay 商户代码
     * @param username Basic Auth 用户名
     * @param password Basic Auth 密码
     */
    record WorldPayJsonMidConfig(String merchantCode, String username, String password) {
    }
}
