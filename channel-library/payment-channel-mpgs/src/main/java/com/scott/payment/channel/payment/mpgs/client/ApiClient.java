package com.scott.payment.channel.payment.mpgs.client;

import com.scott.payment.channel.payment.mpgs.config.ChannelProperties;
import com.scott.payment.channel.payment.mpgs.converter.RequestConverter;
import com.scott.payment.channel.payment.mpgs.converter.ResponseConverter;
import com.scott.payment.channel.payment.mpgs.model.request.RequestPayload;
import com.scott.payment.channel.payment.mpgs.model.request.ThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.mpgs.model.response.ResponsePayload;
import com.scott.payment.channel.payment.mpgs.model.response.ThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.mpgs.support.ApiOperation;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MPGS REST API 客户端，位于 payment-channel-mpgs 渠道实现层，负责构造 MPGS URL、Basic Auth、HTTP 调用和请求响应摘要日志；不处理平台交易状态机。
 */
@Slf4j
public class ApiClient {

    /**
     * MPGS 查询交易使用的 HTTP 方法。
     */
    private static final String HTTP_METHOD_GET = "GET";

    /**
     * MPGS 创建或修改交易使用的 HTTP 方法。
     */
    private static final String HTTP_METHOD_PUT = "PUT";


    private static final String EXT_REQUEST_URL = "requestUrl";

    /**
     * 单笔请求读取超时扩展键，单位为秒。
     */
    private static final String EXT_READ_TIMEOUT_SECONDS = "readTimeoutSeconds";


    private static final String EXT_MPGS_MERCHANT_ID = "mid.merchantId";

    /**
     * 平台统一路由结果中的渠道商户号，作为渠道专属 merchantId 元数据的兼容回退。
     */
    private static final String EXT_MID_NO = "midNo";


    private static final String EXT_MPGS_API_USERNAME = "mid.apiUsername";

    /**
     * MID 密码扩展键；对应值只能用于构造认证头。
     */
    private static final String EXT_MPGS_PASSWORD = "mid.password";

    /**
     * 兼容旧配置名称的 API 密码扩展键。
     */
    private static final String EXT_MPGS_API_PASSWORD = "mid.apiPassword";


    private static final String EXT_MPGS_API_VERSION = "mid.version";

    /**
     * 渠道请求审计中的 HTTP 方法字段名。
     */
    private static final String RAW_HTTP_METHOD = "httpMethod";


    private static final String RAW_REQUEST_URL_MASKED = "requestUrlMasked";


    private static final String RAW_REQUEST_HEADER_JSON_MASKED = "requestHeaderJsonMasked";


    private static final String RAW_REQUEST_BODY_JSON_MASKED = "requestBodyJsonMasked";


    private static final String RAW_RESPONSE_HEADER_JSON_MASKED = "responseHeaderJsonMasked";


    private static final String RAW_RESPONSE_BODY_JSON_MASKED = "responseBodyJsonMasked";

    private static final Pattern MPGS_CARD_NUMBER_PATTERN = Pattern.compile(
            "(\"number\"\\s*:\\s*\")([0-9]{6})([0-9]{1,19})([0-9]{4})(\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MPGS_SECRET_FIELD_PATTERN = Pattern.compile(
            "(\"(?:authenticationToken|apiPassword|threeDSSessionData|threeDSMethodData|creq|cres|PaRes|MD)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MPGS_EXPIRY_PATTERN = Pattern.compile(
            "(\"expiry\"\\s*:\\s*\\{\\s*\"month\"\\s*:\\s*\")([^\"\\\\]*)(\"\\s*,\\s*\"year\"\\s*:\\s*\")([^\"\\\\]*)(\"\\s*})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MPGS_HTML_FIELD_PATTERN = Pattern.compile(
            "(\"html\"\\s*:\\s*\")((?:\\\\.|[^\"\\\\])*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MPGS_SECRET_QUERY_PARAM_PATTERN = Pattern.compile(
            "((?:threeDsReturnToken|creq|cres|PaRes|MD|threeDSSessionData)(?:%3[Dd]|=))([^&\"'\\\\\\s]+?)(?=(?:&|%26|[\"'\\\\\\s]|$))",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * MPGS 渠道配置，包含网关地址、API 版本、商户号和凭据引用；密码只允许用于组装认证头。
     */
    private final ChannelProperties properties;

    /**
     * 平台统一渠道请求到 MPGS JSON 请求体的映射器。
     */
    private final RequestConverter requestConverter;

    /**
     * MPGS 原始响应到平台统一渠道响应的映射器。
     */
    private final ResponseConverter responseConverter;

    /**
     * JDK HTTP 客户端，测试环境可注入替身或访问本地 fake server。
     */
    private final HttpClient httpClient;

    /**
     * 渠道出站地址安全策略。
     */
    private final OutboundHttpUrlPolicy outboundUrlPolicy;

    /**
     * 创建 MPGS API 客户端。
     *
     * @param properties     MPGS 渠道配置
     * @param requestConverter  MPGS 请求映射器
     * @param responseConverter MPGS 响应映射器
     */
    @Autowired
    public ApiClient(ChannelProperties properties,
                         RequestConverter requestConverter,
                         ResponseConverter responseConverter) {
        this(properties, requestConverter, responseConverter,
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY));
    }

    /**
     * 创建按运行环境校验出站地址的 MPGS API 客户端。
     *
     * @param properties MPGS 渠道配置
     * @param requestConverter MPGS 请求映射器
     * @param responseConverter MPGS 响应映射器
     * @param outboundUrlPolicy 出站地址安全策略
     */
    public ApiClient(ChannelProperties properties,
                     RequestConverter requestConverter,
                     ResponseConverter responseConverter,
                     OutboundHttpUrlPolicy outboundUrlPolicy) {
        this(properties, requestConverter, responseConverter, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build(), outboundUrlPolicy);
    }

    /**
     * 创建 MPGS API 客户端。
     *
     * @param properties     MPGS 渠道配置
     * @param requestConverter  MPGS 请求映射器
     * @param responseConverter MPGS 响应映射器
     * @param httpClient     HTTP 客户端，测试可注入替身
     */
    ApiClient(ChannelProperties properties,
                  RequestConverter requestConverter,
                  ResponseConverter responseConverter,
                  HttpClient httpClient) {
        this(properties, requestConverter, responseConverter, httpClient,
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.DEVELOPMENT));
    }

    /**
     * 创建传输层和出站策略均可控的 MPGS 客户端。
     *
     * @param properties MPGS 渠道配置
     * @param requestConverter MPGS 请求映射器
     * @param responseConverter MPGS 响应映射器
     * @param httpClient HTTP 客户端
     * @param outboundUrlPolicy 出站地址安全策略
     */
    ApiClient(ChannelProperties properties,
              RequestConverter requestConverter,
              ResponseConverter responseConverter,
              HttpClient httpClient,
              OutboundHttpUrlPolicy outboundUrlPolicy) {
        this.properties = properties;
        this.requestConverter = requestConverter;
        this.responseConverter = responseConverter;
        this.httpClient = httpClient;
        this.outboundUrlPolicy = outboundUrlPolicy;
    }

    /**
     * 执行 MPGS 渠道请求。
     * <p>
     * 当前方法只完成渠道交互和结果映射：请求日志会输出交易标识、URL、HTTP 方法和脱敏请求体；响应日志会输出 HTTP 状态、
     * 耗时和脱敏响应体。完整卡号、CVV、密码、认证 token 和 Authorization 头不得进入日志。
     *
     * @param request 渠道统一请求
     * @return 渠道统一响应
     */
    public ChannelPaymentResponse execute(ChannelPaymentRequest request) {
        validateProperties(request);
        validateRequest(request);
        long startNanos = System.nanoTime();
        String url = null;
        String httpMethod = null;
        String operation = null;
        String requestBody = null;
        try {
            url = buildTransactionUrl(request);
            HttpResponse<String> response;
            if (ChannelCapability.QUERY.getCode().equals(normalizeType(request.getTransactionType()))) {
                httpMethod = HTTP_METHOD_GET;
                operation = ApiOperation.RETRIEVE;
                logStructuredRequestStart(request, httpMethod, operation, url, null);
                fillRawRequestAudit(request, httpMethod, url, null);
                response = sendGet(request, url);
            } else {
                RequestPayload payload = requestConverter.toRequest(request);
                requestBody = JsonUtils.toJsonString(payload);
                httpMethod = HTTP_METHOD_PUT;
                operation = payload.getApiOperation();
                logStructuredRequestStart(request, httpMethod, operation, url, payload);
                fillRawRequestAudit(request, httpMethod, url, requestBody);
                response = sendPut(request, url, requestBody);
            }
            return handleResponse(request, response, httpMethod, operation, url, startNanos);
        } catch (java.net.http.HttpTimeoutException e) {
            logRequestException(request, httpMethod, operation, url, startNanos, e);
            throw new ChannelTimeoutException("MPGS request timed out", e);
        } catch (IOException e) {
            logRequestException(request, httpMethod, operation, url, startNanos, e);
            throw new ChannelRequestException("MPGS network request failed", e, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logRequestException(request, httpMethod, operation, url, startNanos, e);
            throw new ChannelRequestException("MPGS request was interrupted", e, true);
        }
    }

    /**
     * 调用 MPGS INITIATE_AUTHENTICATION，结果仅描述 3DS 认证准备状态。
     */
    public ThreeDsAuthenticationResponse initiateAuthentication(ThreeDsAuthenticationRequest request) {
        return executeAuthentication(request, ApiOperation.INITIATE_AUTHENTICATION);
    }

    /**
     * 调用 MPGS AUTHENTICATE_PAYER，可能返回 frictionless 结果或 ACS 质询 HTML。
     */
    public ThreeDsAuthenticationResponse authenticatePayer(ThreeDsAuthenticationRequest request) {
        return executeAuthentication(request, ApiOperation.AUTHENTICATE_PAYER);
    }

    /**
     * 使用 Retrieve Transaction 查询 MPGS 认证交易的最终状态。
     *
     * @param request 必须包含原订单号和 authentication transaction id
     * @return 当前 3DS 认证状态和认证证据
     */
    public ThreeDsAuthenticationResponse retrieveAuthentication(ThreeDsAuthenticationRequest request) {
        validateProperties(request);
        long startNanos = System.nanoTime();
        String url = null;
        try {
            url = buildAuthenticationUrl(request);
            log.info("MPGS 3DS查询上下文, event: MPGS_3DS_QUERY_CONTEXT, context: {}", JsonUtils.toJsonString(new ThreeDsRequestLogContext(
                    HTTP_METHOD_GET, ApiOperation.RETRIEVE, request.getOperationId(), request.getTransactionId(),
                    request.getChannelOrderNo(), request.getAuthenticationTransactionId(), request.getMerchantOrderNo()
            )));
            HttpResponse<String> response = sendGet(request, url);
            return handleAuthenticationResponse(
                    request, response, HTTP_METHOD_GET, ApiOperation.RETRIEVE, url, startNanos);
        } catch (java.net.http.HttpTimeoutException e) {
            logThreeDsException(request, ApiOperation.RETRIEVE, url, startNanos, e);
            throw new ChannelTimeoutException("MPGS 3DS retrieve timed out", e);
        } catch (IOException e) {
            logThreeDsException(request, ApiOperation.RETRIEVE, url, startNanos, e);
            throw new ChannelRequestException("MPGS 3DS retrieve network request failed", e, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logThreeDsException(request, ApiOperation.RETRIEVE, url, startNanos, e);
            throw new ChannelRequestException("MPGS 3DS retrieve was interrupted", e, true);
        }
    }

    /**
     * 执行 MPGS 3DS 认证请求，通道层只返回协议结果，不写平台交易状态。
     */
    private ThreeDsAuthenticationResponse executeAuthentication(ThreeDsAuthenticationRequest request,
                                                                    String apiOperation) {
        validateProperties(request);
        long startNanos = System.nanoTime();
        String url = null;
        String requestBody = null;
        try {
            url = buildAuthenticationUrl(request);
            RequestPayload payload = requestConverter.toThreeDsRequest(request, apiOperation);
            requestBody = JsonUtils.toJsonString(payload);
            log.info("MPGS 3DS请求上下文, event: MPGS_3DS_REQUEST_CONTEXT, context: {}", JsonUtils.toJsonString(new ThreeDsRequestLogContext(
                    HTTP_METHOD_PUT, apiOperation, request.getOperationId(), request.getTransactionId(),
                    request.getChannelOrderNo(), request.getAuthenticationTransactionId(), request.getMerchantOrderNo()
            )));
            logPayloadMetadata("MPGS 3DS请求摘要", requestBody);
            HttpResponse<String> response = sendPut(request, url, requestBody);
            return handleAuthenticationResponse(
                    request, response, HTTP_METHOD_PUT, apiOperation, url, startNanos);
        } catch (java.net.http.HttpTimeoutException e) {
            logThreeDsException(request, apiOperation, url, startNanos, e);
            throw new ChannelTimeoutException("MPGS 3DS request timed out", e);
        } catch (IOException e) {
            logThreeDsException(request, apiOperation, url, startNanos, e);
            throw new ChannelRequestException("MPGS 3DS network request failed", e, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logThreeDsException(request, apiOperation, url, startNanos, e);
            throw new ChannelRequestException("MPGS 3DS request was interrupted", e, true);
        }
    }

    /**
     * 处理 MPGS HTTP 响应并记录不可逆响应摘要。
     *
     * @param request    渠道统一请求
     * @param response   MPGS HTTP 响应
     * @param httpMethod HTTP 方法
     * @param operation  MPGS API 操作
     * @param startNanos 请求开始时间，用于计算耗时
     * @return 渠道统一响应
     */
    private ChannelPaymentResponse handleResponse(ChannelPaymentRequest request,
                                                  HttpResponse<String> response,
                                                  String httpMethod,
                                                  String operation,
                                                  String requestUrl,
                                                  long startNanos) {
        String body = response.body();
        if (!StringUtils.hasText(body)) {
            logStructuredResponseEnd(request, null, response, httpMethod, operation, requestUrl, body, startNanos, true);
            throw responseException("MPGS response body is empty", response.statusCode(), null);
        }
        ResponsePayload payload = parseResponseBody(request, body, response.statusCode(), operation, requestUrl, startNanos);
        if (payload == null) {
            logStructuredResponseEnd(request, null, response, httpMethod, operation, requestUrl, body, startNanos, true);
            throw responseException("MPGS parsed response is empty", response.statusCode(), null);
        }
        if (!isSuccessfulHttpStatus(response.statusCode())
                && (!hasMpgsResult(payload) || isOutcomeUncertainHttpStatus(response.statusCode()))) {
            logStructuredResponseEnd(request, null, response, httpMethod, operation, requestUrl, body, startNanos,
                    isOutcomeUncertainHttpStatus(response.statusCode()));
            throw responseException(
                    "MPGS HTTP response is not successful, status: " + response.statusCode(),
                    response.statusCode(), null);
        }
        ChannelPaymentResponse channelResponse = responseConverter.toChannelResponse(request, payload);
        fillRawResponseAudit(channelResponse, request, response, httpMethod, requestUrl);
        logStructuredResponseEnd(request, channelResponse, response, httpMethod, operation, requestUrl, body, startNanos,
                isPendingResult(channelResponse));
        return channelResponse;
    }

    /**
     * 将脱敏后的请求地址、请求头和请求体写入渠道审计字段。
     *
     * @param request 平台统一渠道请求
     * @param httpMethod HTTP 方法
     * @param requestUrl MPGS 请求地址
     * @param requestBody 原始请求体，仅在本方法内完成脱敏
     */
    private void fillRawRequestAudit(ChannelPaymentRequest request, String httpMethod, String requestUrl, String requestBody) {
        if (request == null) {
            return;
        }
        request.getExtension().put(RAW_HTTP_METHOD, httpMethod);
        request.getExtension().put(RAW_REQUEST_URL_MASKED, requestUrl);
        request.getExtension().put(RAW_REQUEST_HEADER_JSON_MASKED, JsonUtils.toJsonString(Collections.singletonMap("Authorization", "Basic ***")));
        request.getExtension().put(RAW_REQUEST_BODY_JSON_MASKED, StringUtils.hasText(requestBody)
                ? maskJson(requestBody)
                : JsonUtils.toJsonString(Collections.emptyMap()));
    }

    /**
     * 将 HTTP 元数据和脱敏响应写入渠道统一响应。
     *
     * @param channelResponse 渠道统一响应
     * @param request 平台统一渠道请求
     * @param response MPGS HTTP 响应
     * @param httpMethod HTTP 方法
     * @param requestUrl MPGS 请求地址
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
        channelResponse.setResponseHeaderJsonMasked(JsonUtils.toJsonString(Collections.emptyMap()));
        channelResponse.setResponseBodyJsonMasked(maskJson(response.body()));
        channelResponse.getRawResponse().put("httpStatus", String.valueOf(response.statusCode()));
        putIfText(channelResponse, RAW_HTTP_METHOD, channelResponse.getHttpMethod());
        putIfText(channelResponse, RAW_REQUEST_URL_MASKED, channelResponse.getRequestUrlMasked());
        putIfText(channelResponse, RAW_REQUEST_HEADER_JSON_MASKED, channelResponse.getRequestHeaderJsonMasked());
        putIfText(channelResponse, RAW_REQUEST_BODY_JSON_MASKED, channelResponse.getRequestBodyJsonMasked());
        putIfText(channelResponse, RAW_RESPONSE_HEADER_JSON_MASKED, channelResponse.getResponseHeaderJsonMasked());
        putIfText(channelResponse, RAW_RESPONSE_BODY_JSON_MASKED, channelResponse.getResponseBodyJsonMasked());
    }

    /**
     * 将非空审计字段写入原始响应摘要。
     *
     * @param channelResponse 渠道统一响应
     * @param key 审计字段名
     * @param value 审计字段值
     */
    private void putIfText(ChannelPaymentResponse channelResponse, String key, String value) {
        if (channelResponse != null && StringUtils.hasText(value)) {
            channelResponse.getRawResponse().put(key, value);
        }
    }

    /**
     * 读取请求扩展中的脱敏审计字段。
     *
     * @param request 平台统一渠道请求
     * @param key 审计字段名
     * @return 审计字段值，缺失时返回 null
     */
    private String auditValue(ChannelPaymentRequest request, String key) {
        if (request == null || request.getExtension() == null) {
            return null;
        }
        return request.getExtension().get(key);
    }

    /**
     * 解析 MPGS 响应并在失败时记录不含原始报文的诊断日志。
     *
     * @param request 平台统一渠道请求
     * @param body MPGS 原始响应体
     * @param httpStatus HTTP 状态码
     * @param operation MPGS API 操作
     * @param requestUrl 请求地址
     * @param startNanos 请求开始时间
     * @return MPGS 响应对象
     */
    private ResponsePayload parseResponseBody(ChannelPaymentRequest request,
                                              String body,
                                              int httpStatus,
                                              String operation,
                                              String requestUrl,
                                              long startNanos) {
        try {
            return JsonUtils.parseObject(body, ResponsePayload.class);
        } catch (RuntimeException e) {
            log.warn("MPGS渠道响应解析失败, event: CHANNEL_RESPONSE_PARSE_FAILED, traceId: {}, channelCode: {}, transactionType: {}, transactionId: {}, operationId: {}, merchantOrderNo: {}, apiOperation: {}, endpointHost: {}, endpointPath: {}, httpStatus: {}, outcomeUncertain: {}, durationMs: {}, exceptionType: {}",
                    TraceContext.getTraceId(), request.getChannelCode(), request.getTransactionType(),
                    request.getTransactionId(), request.getOperationId(), request.getMerchantOrderNo(), operation,
                    host(requestUrl), path(requestUrl), httpStatus, isOutcomeUncertainHttpStatus(httpStatus),
                    elapsedMillis(startNanos), e.getClass().getSimpleName());
            throw responseException("MPGS response parse failed", httpStatus, e);
        }
    }

    /**
     * 明确的客户端请求拒绝可直接失败；可能发生在处理途中的冲突、限流和超时响应仍需后续勾兑。
     */
    private ChannelResponseException responseException(String message, int httpStatus, Throwable cause) {
        return new ChannelResponseException(message, cause, isOutcomeUncertainHttpStatus(httpStatus));
    }

    /**
     * 判断 HTTP 状态是否要求后续通过回调或查询确认结果。
     *
     * @param httpStatus HTTP 状态码
     * @return true 表示当前响应不足以确认交易终态
     */
    private boolean isOutcomeUncertainHttpStatus(int httpStatus) {
        return httpStatus < 400 || httpStatus >= 500
                || httpStatus == 408 || httpStatus == 409 || httpStatus == 425 || httpStatus == 429;
    }

    /**
     * 判断 HTTP 响应是否成功。
     *
     * @param httpStatus HTTP 状态码
     * @return true 表示状态码位于 2xx 区间
     */
    private boolean isSuccessfulHttpStatus(int httpStatus) {
        return httpStatus >= 200 && httpStatus < 300;
    }

    /**
     * 判断响应体是否为 MPGS 标准业务响应。
     * <p>
     * 4xx 可能是渠道业务拒绝而非网络失败，例如卡交易不支持 UPDATE_AUTHORIZATION。此类响应需要映射为渠道失败结果，
     * 让后台看到真实渠道原因；非 MPGS JSON 响应才继续作为渠道响应异常处理。
     *
     * @param payload MPGS 响应载荷
     * @return true 表示可按渠道业务响应映射
     */
    private boolean hasMpgsResult(ResponsePayload payload) {
        return StringUtils.hasText(payload.getResult())
                || payload.getError() != null
                || payload.getResponse() != null;
    }

    /**
     * 使用 PUT 调用 MPGS 交易变更类 API，例如 PAY、AUTHORIZE、CAPTURE、REFUND、VOID 和 UPDATE_AUTHORIZATION。
     *
     * @param url         MPGS 交易 URL
     * @param requestBody 已序列化的 MPGS JSON 请求体
     * @return MPGS HTTP 响应
     * @throws IOException          网络异常
     * @throws InterruptedException 当前线程被中断
     */
    private HttpResponse<String> sendPut(ChannelPaymentRequest request, String url, String requestBody) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout(request))
                .header("Authorization", basicAuthHeader(request))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 使用 PUT 调用 MPGS 3DS API，Basic Auth 头只进入 HTTP 请求，不写日志。
     */
    private HttpResponse<String> sendPut(ThreeDsAuthenticationRequest request, String url, String requestBody) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout(request))
                .header("Authorization", basicAuthHeader(request))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 使用 GET 调用 MPGS 交易查询 API。
     *
     * @param url MPGS 查询 URL
     * @return MPGS HTTP 响应
     * @throws IOException          网络异常
     * @throws InterruptedException 当前线程被中断
     */
    private HttpResponse<String> sendGet(ChannelPaymentRequest request, String url) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout(request))
                .header("Authorization", basicAuthHeader(request))
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 使用 GET 查询 MPGS 3DS authentication transaction。
     */
    private HttpResponse<String> sendGet(ThreeDsAuthenticationRequest request, String url)
            throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout(request))
                .header("Authorization", basicAuthHeader(request))
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 构建 MPGS 交易 URL。
     * <p>
     * URL 中的 orderId 采用平台传入的 channelOrderNo，MPGS transactionId 采用平台生成并持久化的 channelTransactionId。
     * 对 MPGS，channelOrderNo 通常等于原始授权/支付的平台 transactionId。
     *
     * @param request 渠道统一请求
     * @return MPGS REST 交易 URL
     */
    String buildTransactionUrl(ChannelPaymentRequest request) {
        requireText(request.getChannelOrderNo(), "MPGS channelOrderNo is required");
        requireText(request.getChannelTransactionId(), "MPGS channelTransactionId is required");
        String configuredBaseUrl = extensionValue(request, EXT_REQUEST_URL, properties.getBaseUrl());
        String baseUrl = configuredBaseUrl.endsWith("/")
                ? configuredBaseUrl
                : configuredBaseUrl + "/";
        String url = baseUrl
                + "version/" + encode(extensionValue(request, EXT_MPGS_API_VERSION, properties.getVersion()))
                + "/merchant/" + encode(mpgsMerchantId(request))
                + "/order/" + encode(request.getChannelOrderNo())
                + "/transaction/" + encode(request.getChannelTransactionId());
        return validateOutboundUrl(url);
    }

    /**
     * 构造 MPGS 3DS 认证交易地址。
     *
     * @param request 3DS 认证请求
     * @return 包含商户号、订单号和认证交易号的请求地址
     */
    String buildAuthenticationUrl(ThreeDsAuthenticationRequest request) {
        requireText(request.getChannelOrderNo(), "MPGS 3DS channelOrderNo is required");
        requireText(request.getAuthenticationTransactionId(), "MPGS 3DS authenticationTransactionId is required");
        String configuredBaseUrl = extensionValue(request, EXT_REQUEST_URL, properties.getBaseUrl());
        String baseUrl = configuredBaseUrl.endsWith("/")
                ? configuredBaseUrl
                : configuredBaseUrl + "/";
        String url = baseUrl
                + "version/" + encode(extensionValue(request, EXT_MPGS_API_VERSION, properties.getVersion()))
                + "/merchant/" + encode(mpgsMerchantId(request))
                + "/order/" + encode(request.getChannelOrderNo())
                + "/transaction/" + encode(request.getAuthenticationTransactionId());
        return validateOutboundUrl(url);
    }

    /**
     * 在建立网络请求前校验 MPGS 完整目标地址。
     *
     * @param url 完整请求地址
     * @return 规范化后的安全地址
     */
    private String validateOutboundUrl(String url) {
        try {
            return outboundUrlPolicy.validate(url).toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new ChannelRequestException("MPGS requestUrl violates outbound URL policy", exception);
        }
    }

    /**
     * 构造 MPGS Basic Auth 请求头。该值不得写入日志。
     *
     * @return Basic Auth 请求头
     */
    private String basicAuthHeader(ChannelPaymentRequest request) {
        String merchantId = mpgsMerchantId(request);
        String username = extensionValue(request, EXT_MPGS_API_USERNAME, properties.getApiUsername());
        if (!StringUtils.hasText(username)) {
            username = "merchant." + merchantId;
        }
        String raw = username + ":" + mpgsPassword(request);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造 MPGS 3DS Basic Auth 请求头，该值不得进入日志、异常或审计表。
     */
    private String basicAuthHeader(ThreeDsAuthenticationRequest request) {
        String merchantId = mpgsMerchantId(request);
        String username = extensionValue(request, EXT_MPGS_API_USERNAME, properties.getApiUsername());
        if (!StringUtils.hasText(username)) {
            username = "merchant." + merchantId;
        }
        String raw = username + ":" + mpgsPassword(request);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验 MPGS 交易路由结果，并允许使用本地属性作为非生产兜底值。
     * <p>
     * 渠道是否可交易由数据库中的渠道、MID、商户绑定和能力状态统一决定。客户端不再设置额外启用开关，
     * 避免数据库配置已经生效后仍被环境配置阻断；地址、版本和凭证缺失时仍必须在发送 HTTP 请求前失败。
     * </p>
     */
    private void validateProperties(ChannelPaymentRequest request) {
        requireText(extensionValue(request, EXT_REQUEST_URL, properties.getBaseUrl()), "MPGS baseUrl is required");
        requireText(extensionValue(request, EXT_MPGS_API_VERSION, properties.getVersion()), "MPGS version is required");
        requireText(mpgsMerchantId(request), "MPGS merchantId is required");
        requireText(mpgsPassword(request), "MPGS password is required");
    }

    /**
     * 校验 MPGS 3DS 路由结果，支持按数据库 MID 扩展覆盖本地兜底配置。
     */
    private void validateProperties(ThreeDsAuthenticationRequest request) {
        requireText(extensionValue(request, EXT_REQUEST_URL, properties.getBaseUrl()), "MPGS baseUrl is required");
        requireText(extensionValue(request, EXT_MPGS_API_VERSION, properties.getVersion()), "MPGS version is required");
        requireText(mpgsMerchantId(request), "MPGS merchantId is required");
        requireText(mpgsPassword(request), "MPGS password is required");
    }

    /**
     * 解析 MPGS 3DS HTTP 响应，原始响应只保存脱敏文本。
     */
    private ThreeDsAuthenticationResponse handleAuthenticationResponse(ThreeDsAuthenticationRequest request,
                                                                          HttpResponse<String> response,
                                                                          String httpMethod,
                                                                          String apiOperation,
                                                                          String requestUrl,
                                                                          long startNanos) {
        String body = response.body();
        log.info("MPGS 3DS响应上下文, event: MPGS_3DS_RESPONSE_CONTEXT, context: {}", JsonUtils.toJsonString(new ThreeDsResponseLogContext(
                httpMethod, apiOperation, request.getOperationId(), request.getTransactionId(),
                request.getChannelOrderNo(), request.getAuthenticationTransactionId(), response.statusCode(),
                elapsedMillis(startNanos)
        )));
        logPayloadMetadata("MPGS 3DS响应摘要", body);
        if (!StringUtils.hasText(body)) {
            throw responseException("MPGS 3DS response body is empty", response.statusCode(), null);
        }
        ResponsePayload payload;
        try {
            payload = JsonUtils.parseObject(body, ResponsePayload.class);
        } catch (RuntimeException exception) {
            logThreeDsException(request, apiOperation, requestUrl, startNanos, exception);
            throw responseException("MPGS 3DS response parse failed", response.statusCode(), exception);
        }
        if (payload == null) {
            throw responseException("MPGS 3DS parsed response is empty", response.statusCode(), null);
        }
        if (!isSuccessfulHttpStatus(response.statusCode())
                && (!hasMpgsResult(payload) || isOutcomeUncertainHttpStatus(response.statusCode()))) {
            throw responseException(
                    "MPGS 3DS HTTP response is not successful, status: " + response.statusCode(),
                    response.statusCode(), null);
        }
        ThreeDsAuthenticationResponse result = toThreeDsAuthenticationResponse(request, payload);
        result.setRawResponseMasked(maskJson(body));
        result.getExtension().put("httpStatus", String.valueOf(response.statusCode()));
        result.getExtension().put("httpMethod", httpMethod);
        result.getExtension().put("requestUrlMasked", requestUrl);
        return result;
    }

    /**
     * 将 MPGS authentication 节点映射为平台 3DS 协议结果，保留 CAVV/ECI 给后续授权使用。
     */
    private ThreeDsAuthenticationResponse toThreeDsAuthenticationResponse(ThreeDsAuthenticationRequest request,
                                                                             ResponsePayload payload) {
        ThreeDsAuthenticationResponse response = new ThreeDsAuthenticationResponse();
        response.setChannelCode(request.getChannelCode());
        response.setOperationId(request.getOperationId());
        response.setTransactionId(request.getTransactionId());
        response.setChannelOrderNo(request.getChannelOrderNo());
        response.setAuthenticationTransactionId(request.getAuthenticationTransactionId());
        response.setResult(payload == null ? null : payload.getResult());
        ResponsePayload.Response gatewayResponse = payload == null ? null : payload.getResponse();
        ResponsePayload.Authentication authentication = payload == null ? null : payload.getAuthentication();
        ResponsePayload.Transaction transaction = payload == null ? null : payload.getTransaction();
        ResponsePayload.Order order = payload == null ? null : payload.getOrder();
        ResponsePayload.ErrorPayload error = payload == null ? null : payload.getError();
        response.setGatewayCode(gatewayResponse == null ? null : gatewayResponse.getGatewayCode());
        response.setGatewayRecommendation(firstText(
                authentication == null ? null : authentication.getGatewayRecommendation(),
                gatewayResponse == null ? null : gatewayResponse.getGatewayRecommendation()));
        response.setAuthenticationStatus(firstText(
                authentication == null ? null : authentication.getStatus(),
                transaction == null ? null : transaction.getAuthenticationStatus(),
                order == null ? null : order.getAuthenticationStatus()));
        response.setPayerInteraction(authentication == null ? null : authentication.getPayerInteraction());
        response.setThreeDsVersion(authentication == null ? null : authentication.getVersion());
        response.setThreeDsTransactionId(authentication == null || authentication.getThreeDs() == null
                ? null : authentication.getThreeDs().getTransactionId());
        response.setThreeDsServerTransactionId(authentication == null || authentication.getThreeDs2() == null
                ? null : authentication.getThreeDs2().getThreeDSServerTransactionId());
        response.setAcsTransactionId(authentication == null || authentication.getThreeDs2() == null
                ? null : authentication.getThreeDs2().getAcsTransactionId());
        response.setDsTransactionId(firstText(
                authentication == null || authentication.getThreeDs2() == null ? null : authentication.getThreeDs2().getDsTransactionId(),
                authentication == null || authentication.getThreeDs() == null ? null : authentication.getThreeDs().getTransactionId()));
        response.setEci(authentication == null || authentication.getThreeDs() == null
                ? null : authentication.getThreeDs().getAcsEci());
        response.setCavv(authentication == null || authentication.getThreeDs() == null
                ? null : authentication.getThreeDs().getAuthenticationToken());
        response.setRedirectHtml(authentication == null || authentication.getRedirect() == null
                ? null : authentication.getRedirect().getHtml());
        response.setRedirectUrl(authentication == null || authentication.getRedirect() == null
                ? null : authentication.getRedirect().getUrl());
        response.setResponseCode(firstText(
                gatewayResponse == null ? null : gatewayResponse.getAcquirerCode(),
                error == null ? null : error.getCause()));
        response.setResponseMessage(firstText(
                gatewayResponse == null ? null : gatewayResponse.getAcquirerMessage(),
                error == null ? null : error.getExplanation()));
        put(response, "gatewayCode", response.getGatewayCode());
        put(response, "gatewayRecommendation", response.getGatewayRecommendation());
        put(response, "authenticationStatus", response.getAuthenticationStatus());
        put(response, "payerInteraction", response.getPayerInteraction());
        put(response, "threeDsVersion", response.getThreeDsVersion());
        put(response, "threeDsTransactionId", response.getThreeDsTransactionId());
        put(response, "threeDsServerTransactionId", response.getThreeDsServerTransactionId());
        put(response, "acsTransactionId", response.getAcsTransactionId());
        put(response, "dsTransactionId", response.getDsTransactionId());
        put(response, "eci", response.getEci());
        put(response, "providerResult", payload == null ? null : payload.getResult());
        put(response, "errorField", error == null ? null : error.getField());
        put(response, "validationType", error == null ? null : error.getValidationType());
        return response;
    }

    /**
     * 写入非空 3DS 扩展字段，方便上层排查渠道协议差异。
     */
    private void put(ThreeDsAuthenticationResponse response, String key, String value) {
        if (response != null && StringUtils.hasText(value)) {
            response.getExtension().put(key, value);
        }
    }

    /**
     * 校验渠道请求基础字段。
     *
     * @param request 渠道统一请求
     */
    private void validateRequest(ChannelPaymentRequest request) {
        if (request == null) {
            throw new ChannelRequestException("MPGS request is required");
        }
        requireText(request.getTransactionType(), "MPGS transactionType is required");
    }

    /**
     * 打印 MPGS 请求异常日志。
     *
     * @param request    渠道统一请求
     * @param httpMethod HTTP 方法
     * @param operation  MPGS API 操作
     * @param url        MPGS 请求 URL
     * @param startNanos 请求开始时间
     * @param exception  原始异常
     */
    private void logRequestException(ChannelPaymentRequest request,
                                     String httpMethod,
                                     String operation,
                                     String url,
                                     long startNanos,
                                     Exception exception) {
        String event = exception instanceof java.net.http.HttpTimeoutException
                ? "CHANNEL_REQUEST_TIMEOUT" : "CHANNEL_REQUEST_FAILED";
        log.warn("MPGS渠道请求失败, event: {}, traceId: {}, channelCode: {}, transactionType: {}, transactionId: {}, operationId: {}, merchantOrderNo: {}, channelOrderNo: {}, channelTransactionId: {}, apiOperation: {}, endpointHost: {}, endpointPath: {}, httpMethod: {}, midSummary: {}, channelRequestId: {}, outcomeUncertain: true, durationMs: {}, exceptionType: {}",
                event,
                TraceContext.getTraceId(),
                request == null ? null : request.getChannelCode(),
                request == null ? null : request.getTransactionType(),
                safeTransactionId(request),
                safeOperationId(request),
                safeMerchantOrderNo(request),
                safeChannelOrderNo(request),
                safeChannelTransactionId(request),
                operation,
                host(url),
                path(url),
                httpMethod,
                midSummary(request),
                requestId(request),
                elapsedMillis(startNanos),
                exception.getClass().getSimpleName());
    }

    /**
     * 记录 MPGS 渠道请求发起日志。
     * <p>
     * 日志字段覆盖渠道、操作、endpoint 主机与 path、渠道 MID 摘要、平台交易号、
     * 动作单号、渠道请求号以及请求长度和不可逆摘要，不输出完整渠道报文、卡号、CVV 或认证头。
     * </p>
     * @param request 渠道支付请求，提供平台交易标识、渠道码、渠道交易号和扩展字段
     * @param httpMethod HTTP 方法
     * @param operation MPGS API 操作名称
     * @param url MPGS endpoint 完整地址，用于拆分主机和 path
     * @param payload MPGS 请求载荷，仅计算字节长度和不可逆摘要
     */
    private void logStructuredRequestStart(ChannelPaymentRequest request,
                                           String httpMethod,
                                           String operation,
                                           String url,
                                           RequestPayload payload) {
        PayloadLogMetadata metadata = payloadLogMetadata(payload);
        log.info("MPGS渠道请求开始, event: CHANNEL_REQUEST_START, traceId: {}, channelCode: {}, transactionType: {}, transactionId: {}, operationId: {}, merchantOrderNo: {}, channelOrderNo: {}, channelTransactionId: {}, apiOperation: {}, endpointHost: {}, endpointPath: {}, httpMethod: {}, midSummary: {}, channelRequestId: {}, payloadLength: {}, payloadDigest: {}",
                TraceContext.getTraceId(),
                request.getChannelCode(),
                request.getTransactionType(),
                request.getTransactionId(),
                request.getOperationId(),
                request.getMerchantOrderNo(),
                request.getChannelOrderNo(),
                request.getChannelTransactionId(),
                operation,
                host(url),
                path(url),
                httpMethod,
                midSummary(request),
                requestId(request),
                metadata.length(),
                metadata.digest());
    }

    /**
     * 记录 MPGS 渠道响应完成日志。
     * <p>
     * 日志覆盖 HTTP 状态、响应长度和不可逆摘要、渠道结果、收单参考号、响应码、STAN、
     * 渠道交易号和耗时，不记录完整渠道报文。
     * </p>
     * @param request 渠道支付请求，提供平台交易标识、渠道码和扩展请求号
     * @param channelResponse 已映射的渠道响应对象，允许为空
     * @param response HTTP 响应，提供状态码
     * @param httpMethod HTTP 方法
     * @param operation MPGS API 操作名称
     * @param url MPGS endpoint 完整地址，用于拆分主机和 path
     * @param body 渠道原始响应体，仅计算字节长度和不可逆摘要
     * @param startNanos 请求开始时间，单位为纳秒
     * @param outcomeUncertain true 表示仍需回调或查询确认最终结果
     */
    private void logStructuredResponseEnd(ChannelPaymentRequest request,
                                          ChannelPaymentResponse channelResponse,
                                          HttpResponse<String> response,
                                          String httpMethod,
                                          String operation,
                                          String url,
                                          String body,
                                          long startNanos,
                                          boolean outcomeUncertain) {
        PayloadLogMetadata metadata = payloadLogMetadata(body);
        log.info("MPGS渠道响应完成, event: CHANNEL_RESPONSE_END, traceId: {}, channelCode: {}, transactionType: {}, transactionId: {}, operationId: {}, merchantOrderNo: {}, channelOrderNo: {}, channelTransactionId: {}, apiOperation: {}, endpointHost: {}, endpointPath: {}, httpMethod: {}, midSummary: {}, channelRequestId: {}, httpStatus: {}, rawChannelStatus: {}, channelResult: {}, acquirerCode: {}, responseCode: {}, responseMessage: {}, stan: {}, outcomeUncertain: {}, payloadLength: {}, payloadDigest: {}, durationMs: {}",
                TraceContext.getTraceId(),
                request.getChannelCode(),
                request.getTransactionType(),
                request.getTransactionId(),
                request.getOperationId(),
                request.getMerchantOrderNo(),
                channelResponse == null ? request.getChannelOrderNo() : channelResponse.getChannelOrderNo(),
                channelResponse == null ? request.getChannelTransactionId() : channelResponse.getChannelTransactionId(),
                operation,
                host(url),
                path(url),
                httpMethod,
                midSummary(request),
                requestId(request),
                response.statusCode(),
                channelResponse == null ? null : channelResponse.getRawChannelStatus(),
                channelResponse == null ? null : channelResponse.getChannelTradeStatus(),
                rawResponseValue(channelResponse, "acquirerCode"),
                channelResponse == null ? null : channelResponse.getChannelResponseCode(),
                safeMessage(channelResponse == null ? null : channelResponse.getChannelResponseMessage()),
                rawResponseValue(channelResponse, "stan"),
                outcomeUncertain,
                metadata.length(),
                metadata.digest(),
                elapsedMillis(startNanos));
    }

    /**
     * 读取渠道响应扩展中的诊断字段。
     *
     * @param response 渠道统一响应
     * @param key 扩展字段名
     * @return 扩展字段值，响应或字段缺失时返回 null
     */
    private String rawResponseValue(ChannelPaymentResponse response, String key) {
        return response == null || response.getRawResponse() == null ? null : response.getRawResponse().get(key);
    }

    /**
     * 判断映射结果是否仍需后续勾兑。
     *
     * @param response 渠道统一响应
     * @return true 表示结果为待处理或处理中
     */
    private boolean isPendingResult(ChannelPaymentResponse response) {
        if (response == null) {
            return true;
        }
        return "PENDING".equalsIgnoreCase(response.getChannelTradeStatus())
                || "PROCESSING".equalsIgnoreCase(response.getChannelTradeStatus());
    }

    /**
     * 清理渠道响应描述中的控制字符并限制日志长度。
     *
     * @param message 渠道响应描述
     * @return 可安全写入单行日志的摘要
     */
    private String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("(?i)(authorization|password|api[_-]?key|cvv|cvc|pan)\\s*[:=]\\s*[^,;\\s]+", "$1=***")
                .replaceAll("\\b[0-9]{12,19}\\b", "***");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    /**
     * 记录渠道报文的最小元数据，避免完整结构进入应用日志。
     *
     * @param eventName 日志事件名称
     * @param payload 渠道请求或响应对象
     */
    private void logPayloadMetadata(String eventName, Object payload) {
        PayloadLogMetadata metadata = payloadLogMetadata(payload);
        log.info("{}, payloadLength: {}, payloadDigest: {}", eventName, metadata.length(), metadata.digest());
    }

    /**
     * 计算渠道报文 UTF-8 字节长度和 SHA-256 摘要，不保留可逆内容。
     *
     * @param payload 渠道请求或响应对象
     * @return 仅包含长度和不可逆摘要的日志元数据
     */
    private PayloadLogMetadata payloadLogMetadata(Object payload) {
        String text = payload == null ? "" : payload instanceof String value
                ? value
                : JsonUtils.toJsonString(payload);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new PayloadLogMetadata(bytes.length, "sha256:" + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            return new PayloadLogMetadata(bytes.length, "sha256_unavailable");
        }
    }

    /**
     * 提取渠道 endpoint 主机名，用于日志记录渠道访问目标。
     * <p>
     * 返回值不包含 query、认证信息或请求体；入参为空时返回 null。
     * </p>
     * @param url 渠道 endpoint 完整地址
     * @return endpoint 主机名
     */
    private String host(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 提取渠道 endpoint path，用于日志记录 API 访问路径。
     * <p>
     * 返回值不包含 query 参数值、认证信息或请求体；入参为空时返回 null。
     * </p>
     * @param url 渠道 endpoint 完整地址
     * @return endpoint path
     */
    private String path(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).getPath();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 生成 MPGS 商户 MID 摘要。
     * <p>
     * MID 来源于渠道扩展字段或渠道配置；日志只保留首尾少量字符，中间以星号替换，
     * 用于区分渠道账户且避免暴露完整渠道商户号。
     * </p>
     * @param request 渠道支付请求，提供 MPGS MID 扩展字段
     * @return 脱敏后的 MID 摘要；缺失时返回 null
     */
    private String midSummary(ChannelPaymentRequest request) {
        String merchantId = mpgsMerchantId(request);
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        String normalized = merchantId.trim();
        if (normalized.length() <= 6) {
            return "***";
        }
        return normalized.substring(0, 3) + "***" + normalized.substring(normalized.length() - 3);
    }

    /**
     * 读取渠道请求号。
     * <p>
     * 该值来源于渠道请求扩展字段 requestId，用于关联渠道请求开始、响应完成和异常日志；
     * 字段本身不是卡数据、密钥或认证头。
     * </p>
     * @param request 渠道支付请求，允许为空
     * @return 渠道请求号；不存在时返回 null
     */
    private String requestId(ChannelPaymentRequest request) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get("requestId");
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
     * 将平台交易类型标准化为大写编码。
     *
     * @param transactionType 平台交易类型
     * @return 大写交易类型，输入为空时返回空字符串
     */
    private String normalizeType(String transactionType) {
        return transactionType == null ? null : transactionType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 对 URL 路径变量执行 UTF-8 编码。
     *
     * @param value 路径变量
     * @return URL 编码文本
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 校验 MPGS 必填文本。
     *
     * @param value 待校验文本
     * @param message 校验失败说明
     */
    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ChannelRequestException(message);
        }
    }

    /**
     * 解析本次交易的读取超时，非法配置回退至模块默认值。
     *
     * @param request 平台统一渠道请求
     * @return HTTP 读取超时
     */
    private Duration readTimeout(ChannelPaymentRequest request) {
        String configuredSeconds = request == null ? null : request.getExtension().get(EXT_READ_TIMEOUT_SECONDS);
        if (StringUtils.hasText(configuredSeconds)) {
            try {
                return Duration.ofSeconds(Long.parseLong(configuredSeconds));
            } catch (NumberFormatException ignored) {
                return Duration.ofMillis(properties.getReadTimeoutMillis());
            }
        }
        return Duration.ofMillis(properties.getReadTimeoutMillis());
    }

    /**
     * 解析 MPGS 3DS 读超时，路由扩展值非法时退回渠道默认配置。
     */
    private Duration readTimeout(ThreeDsAuthenticationRequest request) {
        String configuredSeconds = request == null ? null : request.getExtension().get(EXT_READ_TIMEOUT_SECONDS);
        if (StringUtils.hasText(configuredSeconds)) {
            try {
                return Duration.ofSeconds(Long.parseLong(configuredSeconds));
            } catch (NumberFormatException ignored) {
                return Duration.ofMillis(properties.getReadTimeoutMillis());
            }
        }
        return Duration.ofMillis(properties.getReadTimeoutMillis());
    }

    /**
     * 按请求扩展和默认值的优先级读取配置。
     *
     * @param request 平台统一渠道请求
     * @param key 扩展字段名
     * @param fallback 默认值
     * @return 首个非空配置值
     */
    private String extensionValue(ChannelPaymentRequest request, String key, String fallback) {
        if (request != null && request.getExtension() != null && StringUtils.hasText(request.getExtension().get(key))) {
            return request.getExtension().get(key);
        }
        return fallback;
    }

    /**
     * 读取 MPGS 3DS 路由扩展配置，优先使用 MID 维度配置，缺失时使用默认值。
     */
    private String extensionValue(ThreeDsAuthenticationRequest request, String key, String fallback) {
        if (request != null && request.getExtension() != null && StringUtils.hasText(request.getExtension().get(key))) {
            return request.getExtension().get(key);
        }
        return fallback;
    }

    /**
     * 按渠道专属元数据、平台统一 MID、渠道本地配置的顺序解析交易 merchantId。
     */
    private String mpgsMerchantId(ChannelPaymentRequest request) {
        return firstText(
                extensionValue(request, EXT_MPGS_MERCHANT_ID, null),
                extensionValue(request, EXT_MID_NO, null),
                properties.getMerchantId());
    }

    /**
     * 按渠道专属元数据、平台统一 MID、渠道本地配置的顺序解析 3DS merchantId。
     */
    private String mpgsMerchantId(ThreeDsAuthenticationRequest request) {
        return firstText(
                extensionValue(request, EXT_MPGS_MERCHANT_ID, null),
                extensionValue(request, EXT_MID_NO, null),
                properties.getMerchantId());
    }

    /**
     * 返回首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本，全部为空时返回 null
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 解析 MPGS MID 密码。
     * <p>
     * 后台 MID 元数据标准字段为 password，渠道扩展参数会带上 mid. 前缀；apiPassword 仅作为历史字段兼容，
     * 避免旧测试配置或旧数据在迁移窗口内直接失效。
     *
     * @param request 渠道统一请求
     * @return MPGS Basic Auth 密码
     */
    private String mpgsPassword(ChannelPaymentRequest request) {
        String password = extensionValue(request, EXT_MPGS_PASSWORD, null);
        if (StringUtils.hasText(password)) {
            return password;
        }
        return extensionValue(request, EXT_MPGS_API_PASSWORD, properties.getApiPassword());
    }

    /**
     * 解析 MPGS 3DS MID 密码，兼容 password 和 apiPassword 两种历史键。
     */
    private String mpgsPassword(ThreeDsAuthenticationRequest request) {
        String password = extensionValue(request, EXT_MPGS_PASSWORD, null);
        if (StringUtils.hasText(password)) {
            return password;
        }
        return extensionValue(request, EXT_MPGS_API_PASSWORD, properties.getApiPassword());
    }

    /**
     * 记录 MPGS 3DS 异常上下文，日志只包含交易标识和错误摘要。
     */
    private void logThreeDsException(ThreeDsAuthenticationRequest request,
                                     String operation,
                                     String url,
                                     long startNanos,
                                     Exception exception) {
        log.warn("MPGS 3DS请求失败, event: CHANNEL_3DS_REQUEST_FAILED, traceId: {}, channelCode: {}, apiOperation: {}, endpointHost: {}, endpointPath: {}, httpMethod: {}, transactionId: {}, operationId: {}, channelOrderNo: {}, authenticationTransactionId: {}, merchantOrderNo: {}, durationMs: {}, exceptionType: {}",
                TraceContext.getTraceId(),
                request == null ? null : request.getChannelCode(),
                operation,
                host(url),
                path(url),
                HTTP_METHOD_PUT,
                request == null ? null : request.getTransactionId(),
                request == null ? null : request.getOperationId(),
                request == null ? null : request.getChannelOrderNo(),
                request == null ? null : request.getAuthenticationTransactionId(),
                request == null ? null : request.getMerchantOrderNo(),
                elapsedMillis(startNanos), exception.getClass().getSimpleName());
    }

    /**
     * 安全读取平台操作号。
     *
     * @param request 渠道请求，可为空
     * @return 平台操作号
     */
    private String safeOperationId(ChannelPaymentRequest request) {
        return request == null ? null : request.getOperationId();
    }

    /**
     * 安全读取平台交易号。
     *
     * @param request 渠道请求，可为空
     * @return 平台交易号
     */
    private String safeTransactionId(ChannelPaymentRequest request) {
        return request == null ? null : request.getTransactionId();
    }

    /**
     * 安全读取渠道订单号。
     *
     * @param request 渠道请求，可为空
     * @return 渠道订单号
     */
    private String safeChannelOrderNo(ChannelPaymentRequest request) {
        return request == null ? null : request.getChannelOrderNo();
    }

    /**
     * 安全读取渠道交易号。
     *
     * @param request 渠道请求，可为空
     * @return 渠道交易号
     */
    private String safeChannelTransactionId(ChannelPaymentRequest request) {
        return request == null ? null : request.getChannelTransactionId();
    }

    /**
     * 安全读取商户订单号。
     *
     * @param request 渠道请求，可为空
     * @return 商户订单号
     */
    private String safeMerchantOrderNo(ChannelPaymentRequest request) {
        return request == null ? null : request.getMerchantOrderNo();
    }

    /**
     * 对 MPGS JSON 请求/响应执行脱敏。
     * <p>
     * 该方法会先复用公共强脱敏工具隐藏付款人 IP、姓名、联系方式和账单地址，再补充 MPGS 特有字段：
     * sourceOfFunds.provided.card.number、expiry.month/year 和 authentication.threeDs.authenticationToken。
     * 渠道审计、测试日志和断言都应复用此方法，避免敏感字段因协议命名差异泄露。
     *
     * @param json MPGS 原始 JSON
     * @return 脱敏后的 JSON
     */
    public static String maskJson(String json) {
        String masked = SensitiveDataMaskUtils.maskJsonSafely(json);
        if (masked == null || masked.isEmpty()) {
            return masked;
        }
        masked = MPGS_CARD_NUMBER_PATTERN.matcher(masked).replaceAll(matchResult -> Matcher.quoteReplacement(
                matchResult.group(1)
                        + matchResult.group(2)
                        + "******"
                        + matchResult.group(4)
                        + matchResult.group(5)
        ));
        masked = MPGS_EXPIRY_PATTERN.matcher(masked).replaceAll("$1***$3***$5");
        masked = MPGS_SECRET_FIELD_PATTERN.matcher(masked).replaceAll("$1***$3");
        masked = MPGS_SECRET_QUERY_PARAM_PATTERN.matcher(masked).replaceAll("$1***");
        return MPGS_HTML_FIELD_PATTERN.matcher(masked).replaceAll("$1***$3");
    }

    /**
     * 保存不含认证报文和卡数据的 3DS 请求日志上下文。
     */
    private record ThreeDsRequestLogContext(String httpMethod,
                                            String operation,
                                            String operationId,
                                            String transactionId,
                                            String channelOrderNo,
                                            String authenticationTransactionId,
                                            String merchantOrderNo) {
    }

    /**
     * 保存不含认证报文和卡数据的 3DS 响应日志上下文。
     */
    private record ThreeDsResponseLogContext(String httpMethod,
                                             String operation,
                                             String operationId,
                                             String transactionId,
                                             String channelOrderNo,
                                             String authenticationTransactionId,
                                             int httpStatus,
                                             long durationMillis) {
    }

    /**
     * 保存渠道报文字节数和不可逆摘要。
     *
     * @param length UTF-8 报文字节数
     * @param digest SHA-256 摘要
     */
    private record PayloadLogMetadata(int length, String digest) {
    }
}
