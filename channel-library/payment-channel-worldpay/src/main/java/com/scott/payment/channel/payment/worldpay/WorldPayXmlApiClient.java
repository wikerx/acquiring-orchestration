package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
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
 * @classname : WorldPayXmlApiClient
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay XML HTTP 客户端，位于 payment-channel-worldpay 渠道实现层，负责读取后台 MID 三要素、发送 WPG XML Direct 请求、记录脱敏报文并映射渠道 XML 响应。
 * @status : create
 */
@Slf4j
public class WorldPayXmlApiClient {

    /**
     * {@code EXT_REQUEST_URL}，表示当前内部调用、渠道调用或商户通知的目标地址。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String EXT_REQUEST_URL = "requestUrl";

    /**
     * 渠道读取超时时间扩展键。
     * <p>
     * 单位：秒；格式：正整数文本；非敏感字段；允许为空，默认 30 秒。
     * 数据来源：渠道 MID 扩展配置，用于控制 HTTP client 单次等待渠道响应的最长时间。
     * </p>
     */
    private static final String EXT_READ_TIMEOUT_SECONDS = "readTimeoutSeconds";

    /**
     * 原始HTTP方式，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String RAW_HTTP_METHOD = "httpMethod";

    /**
     * 原始请求URL脱敏，表示当前内部调用、渠道调用或商户通知的目标地址。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String RAW_REQUEST_URL_MASKED = "requestUrlMasked";

    /**
     * {@code RAW_REQUEST_HEADER_JSON_MASKED}，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String RAW_REQUEST_HEADER_JSON_MASKED = "requestHeaderJsonMasked";

    /**
     * {@code RAW_REQUEST_BODY_JSON_MASKED}，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String RAW_REQUEST_BODY_JSON_MASKED = "requestBodyJsonMasked";

    /**
     * 脱敏响应头审计字段名。
     * <p>
     * 单位：无；格式：JSON 字符串；非敏感字段；允许为空。
     * 数据来源：Worldpay HTTP 响应头白名单，当前仅保留 Content-Type 和 WP-CorrelationId。
     * </p>
     */
    private static final String RAW_RESPONSE_HEADER_JSON_MASKED = "responseHeaderJsonMasked";

    /**
     * 脱敏响应体审计字段名。
     * <p>
     * 单位：无；格式：XML 字符串；敏感节点已掩码；允许为空。
     * 数据来源：Worldpay XML 响应原文，用于交易排查和对账辅助。
     * </p>
     */
    private static final String RAW_RESPONSE_BODY_JSON_MASKED = "responseBodyJsonMasked";

    /**
     * WPG XML Direct 固定 HTTP 方法。
     */
    private static final String HTTP_METHOD_POST = "POST";

    /**
     * WPG XML Direct 默认请求媒体类型。
     */
    private static final String DEFAULT_CONTENT_TYPE = "text/xml; charset=UTF-8";

    /**
     * WPG XML Direct 默认响应媒体类型。
     */
    private static final String DEFAULT_ACCEPT = "text/xml";

    /**
     * Worldpay XML Direct 默认支付服务路径。
     */
    private static final String DEFAULT_ENDPOINT_PATH = "/jsp/merchant/xml/paymentService.jsp";

    /**
     * XML cardNumber 脱敏规则。
     * <p>
     * 单位：无；格式：正则表达式；敏感控制字段；不允许为空。
     * 用于日志和审计字段只保留 PAN 前 6 后 4，禁止完整卡号出现在本地日志。
     * </p>
     */
    private static final Pattern XML_PAN_PATTERN = Pattern.compile(
            "(<cardNumber>)([0-9]{6})([0-9]{1,19})([0-9]{4})(</cardNumber>)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * XML 认证密钥和认证值节点脱敏规则。
     * <p>
     * 单位：无；格式：正则表达式；敏感控制字段；不允许为空。
     * 覆盖 CVC、CAVV、Basic Auth 密码等高敏感数据，避免写入日志、异常和渠道审计记录。
     * </p>
     */
    private static final Pattern XML_SECRET_ELEMENT_PATTERN = Pattern.compile(
            "(<(?:cvc|cavv|password|apiPassword|interfacePassword)>)([^<]*)(</(?:cvc|cavv|password|apiPassword|interfacePassword)>)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * XML 卡有效期属性脱敏规则。
     */
    private static final Pattern XML_EXPIRY_DATE_PATTERN = Pattern.compile(
            "(<expiryDate>\\s*<date\\s+month=\")([^\"]*)(\"\\s+year=\")([^\"]*)(\"\\s*/>\\s*</expiryDate>)",
            Pattern.CASE_INSENSITIVE
    );

    /** XML elements containing cardholder or shopper personal data. */
    private static final Pattern XML_PERSONAL_ELEMENT_PATTERN = Pattern.compile(
            "(<(?:cardHolderName|address1|postalCode|city|state|shopperEmailAddress|authenticatedShopperID|userAgentHeader)>)([^<]*)(</(?:cardHolderName|address1|postalCode|city|state|shopperEmailAddress|authenticatedShopperID|userAgentHeader)>)",
            Pattern.CASE_INSENSITIVE
    );

    /** Shopper IP is an XML attribute and therefore needs a dedicated rule. */
    private static final Pattern XML_SHOPPER_IP_PATTERN = Pattern.compile(
            "(\\bshopperIPAddress=\")([^\"]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    private final WorldPayXmlRequestMapper requestMapper;

    private final WorldPayXmlResponseMapper responseMapper;

    private final HttpClient httpClient;

    @Autowired
    public WorldPayXmlApiClient(WorldPayXmlRequestMapper requestMapper,
                                WorldPayXmlResponseMapper responseMapper) {
        this(requestMapper, responseMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    /**
     * 创建可注入 HTTP client 的 Worldpay XML HTTP 客户端。
     * <p>
     * 该构造器用于单元测试断言请求 URL、请求头和请求体；生产链路使用 public 构造器。
     * </p>
     *
     * @param requestMapper WPGXML 请求映射器
     * @param responseMapper WPGXML 响应映射器
     * @param httpClient HTTP 客户端替身或真实实例
     */
    WorldPayXmlApiClient(WorldPayXmlRequestMapper requestMapper,
                         WorldPayXmlResponseMapper responseMapper,
                         HttpClient httpClient) {
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.httpClient = httpClient;
    }

    /**
     * 调用 Worldpay XML API，并将 HTTP、XML 解析及渠道错误统一映射为渠道响应语义。
     *
     * @param request 已完成路由和金额币种校验的渠道请求
     * @return 脱敏且结构化的 Worldpay 渠道响应
     */
    public ChannelPaymentResponse execute(ChannelPaymentRequest request) {
        validateRequest(request);
        WorldPayXmlMidConfig midConfig = resolveMidConfig(request);
        long startNanos = System.nanoTime();
        String url = null;
        String requestBody = null;
        try {
            url = buildUrl(request);
            requestBody = requestMapper.toWorldPayRequest(request, midConfig.merchantCode());
            logRequest(request, url, requestBody, midConfig);
            fillRawRequestAudit(request, url, requestBody);
            HttpResponse<String> response = sendPost(url, requestBody, midConfig, request);
            return handleResponse(request, response, url, startNanos);
        } catch (java.net.http.HttpTimeoutException exception) {
            logRequestException(request, url, midConfig, startNanos, exception);
            throw new ChannelTimeoutException("WorldPay XML request timed out", exception);
        } catch (IOException exception) {
            logRequestException(request, url, midConfig, startNanos, exception);
            throw new ChannelRequestException("WorldPay XML network request failed", exception, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logRequestException(request, url, midConfig, startNanos, exception);
            throw new ChannelRequestException("WorldPay XML request was interrupted", exception, true);
        }
    }

    /**
     * 构造 Worldpay XML Direct endpoint。
     * <p>
     * baseUrl 可以直接配置为 paymentService.jsp，也可以配置为主机地址并通过 endpointPath 拼接默认 XML Direct 路径。
     * 返回值用于 HTTP 请求和脱敏审计字段，不允许携带 Basic Auth 凭据。
     * </p>
     *
     * @param request 平台统一渠道请求，扩展字段中携带 requestUrl、mid.baseUrl 或 endpointPath
     * @return Worldpay XML Direct 完整请求 URL
     */
    String buildUrl(ChannelPaymentRequest request) {
        String baseUrl = requiredText(firstText(extensionValue(request, EXT_REQUEST_URL), extensionValue(request, "mid.baseUrl")),
                "WorldPay XML requestUrl is required");
        String endpointPath = firstText(extensionValue(request, "mid.endpointPath"), extensionValue(request, "mid.paymentPath"));
        if (baseUrl.endsWith(".jsp") || baseUrl.endsWith(".xml")) {
            return baseUrl;
        }
        return appendPath(baseUrl, firstText(endpointPath, DEFAULT_ENDPOINT_PATH));
    }

    /**
     * 发送 Worldpay XML Direct POST 请求。
     * <p>
     * 方法只把 Basic Auth 放入真实 HTTP 请求头，不写入日志；请求体由 XML 编码器生成，调用方已完成脱敏审计落点填充。
     * </p>
     *
     * @param url Worldpay XML Direct endpoint
     * @param requestBody WPGXML 请求原文，包含本次渠道交易所需字段
     * @param midConfig Worldpay XML MID 三要素，密码仅用于构造 Authorization 头
     * @param request 平台统一渠道请求，用于读取超时和媒体类型配置
     * @return Worldpay HTTP 响应
     * @throws IOException 网络 IO 异常
     * @throws InterruptedException 当前线程被中断
     */
    private HttpResponse<String> sendPost(String url,
                                          String requestBody,
                                          WorldPayXmlMidConfig midConfig,
                                          ChannelPaymentRequest request) throws IOException, InterruptedException {
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
     * 处理 Worldpay XML HTTP 响应。
     * <p>
     * 方法记录响应结束日志和耗时，校验 HTTP 状态与响应体，再委托响应映射器转换为平台统一渠道响应。
     * HTTP 非 2xx 或空响应体会抛出渠道响应异常；本方法不做平台终态推进。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @param response Worldpay HTTP 响应
     * @param requestUrl 本次请求 URL
     * @param startNanos 请求开始时间，单位纳秒
     * @return 平台统一渠道响应
     */
    private ChannelPaymentResponse handleResponse(ChannelPaymentRequest request,
                                                  HttpResponse<String> response,
                                                  String requestUrl,
                                                  long startNanos) {
        String body = response.body();
        WorldPayPayloadLogMetadata metadata = WorldPayPayloadLogMetadata.from(body);
        log.info("event: CHANNEL_RESPONSE_END traceId: {} channelCode: {} apiOperation: {} endpointHost: {} endpointPath: {} httpMethod: {} midSummary: {} transactionId: {} operationId: {} channelRequestId: {} httpStatus: {} payloadLength: {} payloadDigest: {} channelResult: {} responseCode: {} channelTransactionId: {} durationMs: {}",
                TraceContext.getTraceId(),
                request.getChannelCode(),
                operation(request),
                host(requestUrl),
                path(requestUrl),
                HTTP_METHOD_POST,
                midSummary(merchantCode(request)),
                request.getTransactionId(),
                request.getOperationId(),
                requestId(request),
                response.statusCode(),
                metadata.length(),
                metadata.digest(),
                rawStatus(body),
                rawResponseCode(body),
                rawChannelTransactionId(body),
                elapsedMillis(startNanos));
        if (!StringUtils.hasText(body)) {
            throw new ChannelResponseException("WorldPay XML response body is empty");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ChannelResponseException("WorldPay XML HTTP response is not successful, status: " + response.statusCode());
        }
        ChannelPaymentResponse channelResponse = responseMapper.toChannelResponse(request, body);
        fillRawResponseAudit(channelResponse, request, response, requestUrl);
        return channelResponse;
    }

    /**
     * 记录 Worldpay XML 请求开始日志。
     * <p>
     * 日志用于定位商户交易到渠道请求的边界，只输出 traceId、交易号、操作号、endpoint 摘要、MID 摘要和脱敏 XML。
     * PAN、CVC、CAVV、持卡人地址、邮箱和 Basic Auth 密码不得明文输出。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @param url Worldpay XML Direct endpoint
     * @param requestBody WPGXML 请求原文
     * @param midConfig Worldpay XML MID 配置
     */
    private void logRequest(ChannelPaymentRequest request,
                            String url,
                            String requestBody,
                            WorldPayXmlMidConfig midConfig) {
        WorldPayPayloadLogMetadata metadata = WorldPayPayloadLogMetadata.from(requestBody);
        log.info("event: CHANNEL_REQUEST_START traceId: {} channelCode: {} apiOperation: {} endpointHost: {} endpointPath: {} httpMethod: {} midSummary: {} transactionId: {} operationId: {} channelRequestId: {} channelTransactionId: {} payloadLength: {} payloadDigest: {}",
                TraceContext.getTraceId(),
                request.getChannelCode(),
                operation(request),
                host(url),
                path(url),
                HTTP_METHOD_POST,
                midSummary(midConfig.merchantCode()),
                request.getTransactionId(),
                request.getOperationId(),
                requestId(request),
                request.getChannelTransactionId(),
                metadata.length(),
                metadata.digest());
    }

    /**
     * 记录 Worldpay XML 请求异常日志。
     * <p>
     * 用于区分网络异常、超时和线程中断；日志保留耗时、交易标识和异常类型，不输出完整敏感报文。
     * </p>
     *
     * @param request 平台统一渠道请求，异常发生在校验前时允许为空
     * @param url Worldpay XML Direct endpoint，构造失败时允许为空
     * @param midConfig Worldpay XML MID 配置，解析失败时允许为空
     * @param startNanos 请求开始时间，单位纳秒
     * @param exception 原始异常
     */
    private void logRequestException(ChannelPaymentRequest request,
                                     String url,
                                     WorldPayXmlMidConfig midConfig,
                                     long startNanos,
                                     Exception exception) {
        log.warn("event: CHANNEL_REQUEST_FAILED traceId: {} channelCode: {} apiOperation: {} endpointHost: {} endpointPath: {} httpMethod: {} midSummary: {} transactionId: {} operationId: {} channelRequestId: {} channelTransactionId: {} durationMs: {} exceptionType: {}",
                TraceContext.getTraceId(),
                request == null ? null : request.getChannelCode(),
                operation(request),
                host(url),
                path(url),
                HTTP_METHOD_POST,
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
     * 填充渠道请求审计字段。
     * <p>
     * 这些字段会随 ChannelPaymentRequest 传回 service-payment 写入渠道请求记录；请求头和请求体必须先脱敏。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @param requestUrl Worldpay XML Direct endpoint
     * @param requestBody WPGXML 请求原文
     */
    private void fillRawRequestAudit(ChannelPaymentRequest request, String requestUrl, String requestBody) {
        if (request == null) {
            return;
        }
        request.getExtension().put(RAW_HTTP_METHOD, HTTP_METHOD_POST);
        request.getExtension().put(RAW_REQUEST_URL_MASKED, requestUrl);
        request.getExtension().put(RAW_REQUEST_HEADER_JSON_MASKED, JsonUtils.toJsonString(Collections.singletonMap("Authorization", "Basic ***")));
        request.getExtension().put(RAW_REQUEST_BODY_JSON_MASKED, maskWorldPayXml(requestBody));
    }

    /**
     * 填充渠道响应审计字段。
     * <p>
     * 方法把 HTTP 状态、请求摘要、响应头白名单和脱敏响应体写入统一渠道响应，供交易流水、排查日志和人工复盘使用。
     * 不写入 Authorization、完整卡号、CVC、CAVV 或个人地址明文。
     * </p>
     *
     * @param channelResponse 平台统一渠道响应
     * @param request 平台统一渠道请求
     * @param response Worldpay HTTP 响应
     * @param requestUrl Worldpay XML Direct endpoint
     */
    private void fillRawResponseAudit(ChannelPaymentResponse channelResponse,
                                      ChannelPaymentRequest request,
                                      HttpResponse<String> response,
                                      String requestUrl) {
        if (channelResponse == null || response == null) {
            return;
        }
        channelResponse.setHttpStatus(response.statusCode());
        channelResponse.setHttpMethod(firstText(auditValue(request, RAW_HTTP_METHOD), HTTP_METHOD_POST));
        channelResponse.setRequestUrlMasked(firstText(requestUrl, auditValue(request, RAW_REQUEST_URL_MASKED)));
        channelResponse.setRequestHeaderJsonMasked(auditValue(request, RAW_REQUEST_HEADER_JSON_MASKED));
        channelResponse.setRequestBodyJsonMasked(auditValue(request, RAW_REQUEST_BODY_JSON_MASKED));
        channelResponse.setResponseHeaderJsonMasked(responseHeadersMasked(response));
        channelResponse.setResponseBodyJsonMasked(maskWorldPayXml(response.body()));
        channelResponse.getRawResponse().put("httpStatus", String.valueOf(response.statusCode()));
        putIfText(channelResponse, RAW_HTTP_METHOD, channelResponse.getHttpMethod());
        putIfText(channelResponse, RAW_REQUEST_URL_MASKED, channelResponse.getRequestUrlMasked());
        putIfText(channelResponse, RAW_REQUEST_HEADER_JSON_MASKED, channelResponse.getRequestHeaderJsonMasked());
        putIfText(channelResponse, RAW_REQUEST_BODY_JSON_MASKED, channelResponse.getRequestBodyJsonMasked());
        putIfText(channelResponse, RAW_RESPONSE_HEADER_JSON_MASKED, channelResponse.getResponseHeaderJsonMasked());
        putIfText(channelResponse, RAW_RESPONSE_BODY_JSON_MASKED, channelResponse.getResponseBodyJsonMasked());
    }

    /**
     * 解析并校验 Worldpay XML MID 三要素。
     * <p>
     * merchantCode 用于 paymentService 根节点，username/password 用于 Basic Auth；密码只在内存中参与请求头构造，不进入日志或审计字段。
     * </p>
     *
     * @param request 平台统一渠道请求，扩展字段包含后台 MID 配置
     * @return Worldpay XML MID 配置
     */
    private WorldPayXmlMidConfig resolveMidConfig(ChannelPaymentRequest request) {
        String merchantCode = requiredText(firstText(
                extensionValue(request, "mid.worldpayMerchantCode"),
                extensionValue(request, "mid.merchantCode"),
                extensionValue(request, "mid.channelMid"),
                extensionValue(request, "mid.merchantId"),
                extensionValue(request, "mid.merchantNo"),
                extensionValue(request, "mid.midNo"),
                extensionValue(request, "midNo")
        ), "WorldPay XML merchantCode is required");
        String username = requiredText(firstText(
                extensionValue(request, "mid.basicAuthUsername"),
                extensionValue(request, "mid.basicAuthenticationUsername"),
                extensionValue(request, "mid.username"),
                extensionValue(request, "mid.apiUsername"),
                extensionValue(request, "mid.userName")
        ), "WorldPay XML Basic Auth username is required");
        String password = requiredText(firstText(
                extensionValue(request, "mid.basicAuthPassword"),
                extensionValue(request, "mid.basicAuthenticationPassword"),
                extensionValue(request, "mid.interfacePassword"),
                extensionValue(request, "mid.password"),
                extensionValue(request, "mid.apiPassword")
        ), "WorldPay XML Basic Auth password is required");
        return new WorldPayXmlMidConfig(merchantCode, username, password);
    }

    /**
     * 构造 Basic Auth 请求头。
     * <p>
     * 返回值只用于真实 HTTP 请求；日志和 rawResponse 中只能保存 Basic ***。
     * </p>
     *
     * @param midConfig Worldpay XML MID 配置
     * @return Basic Auth Header 值
     */
    private String basicAuthHeader(WorldPayXmlMidConfig midConfig) {
        String raw = midConfig.username() + ":" + midConfig.password();
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验 Worldpay XML 调用基础字段。
     * <p>
     * 交易类型决定 submit、modify 或 inquiry 节点；渠道订单号或平台交易号用于生成 orderCode。
     * 缺失必要字段时抛出请求异常，调用方可据此定位配置或请求装配问题。
     * </p>
     *
     * @param request 平台统一渠道请求
     */
    private void validateRequest(ChannelPaymentRequest request) {
        if (request == null) {
            throw new ChannelRequestException("WorldPay XML request is required");
        }
        requiredText(request.getTransactionType(), "WorldPay XML transactionType is required");
        requiredText(firstText(request.getChannelOrderNo(), request.getTransactionId()), "WorldPay XML channelOrderNo is required");
    }

    /**
     * 拼接 base URL 与 endpoint path。
     * <p>
     * path 为绝对 URL 时直接返回，便于使用 Worldpay 返回的动作链接或环境差异化配置。
     * </p>
     *
     * @param baseUrl Worldpay XML base URL
     * @param path endpoint path 或绝对 URL
     * @return 完整请求 URL
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
     * 解析渠道读取超时时间。
     * <p>
     * 配置非法时回退 30 秒，避免错误配置导致线程无限等待；单位为秒。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return HTTP 请求读取超时时间
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
     * 解析 XML 请求 Content-Type。
     *
     * @param request 平台统一渠道请求
     * @return HTTP Content-Type，默认 text/xml; charset=UTF-8
     */
    private String contentType(ChannelPaymentRequest request) {
        return firstText(extensionValue(request, "mid.contentType"), extensionValue(request, "mid.mediaType"), DEFAULT_CONTENT_TYPE);
    }

    /**
     * 解析 XML 请求 Accept。
     *
     * @param request 平台统一渠道请求
     * @return HTTP Accept，默认 text/xml
     */
    private String accept(ChannelPaymentRequest request) {
        return firstText(extensionValue(request, "mid.accept"), extensionValue(request, "mid.mediaType"), DEFAULT_ACCEPT);
    }

    /**
     * 生成脱敏响应头 JSON。
     * <p>
     * 仅保留排查渠道问题需要的受控头，避免把 Set-Cookie、Authorization 等敏感头写入审计字段。
     * </p>
     *
     * @param response Worldpay HTTP 响应
     * @return 脱敏响应头 JSON
     */
    private String responseHeadersMasked(HttpResponse<String> response) {
        if (response == null || response.headers() == null) {
            return JsonUtils.toJsonString(Collections.emptyMap());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().firstValue("Content-Type").ifPresent(value -> headers.put("Content-Type", value));
        response.headers().firstValue("WP-CorrelationId").ifPresent(value -> headers.put("WP-CorrelationId", value));
        return JsonUtils.toJsonString(headers);
    }

    /**
     * 解析本次渠道操作名称。
     *
     * @param request 平台统一渠道请求
     * @return 大写交易类型；请求为空时返回 null
     */
    private String operation(ChannelPaymentRequest request) {
        if (request == null || !StringUtils.hasText(request.getTransactionType())) {
            return null;
        }
        return request.getTransactionType().trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 读取 Worldpay XML merchantCode。
     *
     * @param request 平台统一渠道请求
     * @return merchantCode，来自后台 MID 配置或兼容字段
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
     * 生成 MID 摘要。
     * <p>
     * 日志只保留首三后三字符，避免商户渠道号完整扩散到普通运行日志。
     * </p>
     *
     * @param merchantCode Worldpay merchantCode
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
     * 读取请求审计字段。
     *
     * @param request 平台统一渠道请求
     * @param key 审计字段名
     * @return 审计字段值
     */
    private String auditValue(ChannelPaymentRequest request, String key) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get(key);
    }

    /**
     * 读取渠道扩展字段。
     *
     * @param request 平台统一渠道请求
     * @param key 扩展字段名
     * @return 扩展字段值
     */
    private String extensionValue(ChannelPaymentRequest request, String key) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get(key);
    }

    /**
     * 读取渠道请求号。
     *
     * @param request 平台统一渠道请求
     * @return requestId 扩展字段；未生成时返回 null
     */
    private String requestId(ChannelPaymentRequest request) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get("requestId");
    }

    /**
     * 提取 endpoint 主机名。
     *
     * @param url 请求 URL
     * @return endpoint host；URL 为空时返回 null
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
     * @return endpoint path；URL 为空时返回 null
     */
    private String path(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        return URI.create(url).getPath();
    }

    /**
     * 计算渠道请求耗时。
     *
     * @param startNanos 请求开始时间，单位纳秒
     * @return 毫秒耗时
     */
    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /**
     * 写入非空渠道响应扩展字段。
     *
     * @param channelResponse 平台统一渠道响应
     * @param key 扩展字段名
     * @param value 扩展字段值
     */
    private void putIfText(ChannelPaymentResponse channelResponse, String key, String value) {
        if (channelResponse != null && StringUtils.hasText(value)) {
            channelResponse.getRawResponse().put(key, value);
        }
    }

    /**
     * 校验并返回必填文本。
     *
     * @param value 原始文本
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
     * @return 首个非空文本；全部为空时返回 null
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 对 Worldpay XML 报文执行日志脱敏。
     * <p>
     * 覆盖 PAN、有效期、CVC、CAVV、个人信息和 Basic Auth 密码；该方法仅用于渠道审计字段。
     * </p>
     *
     * @param xml Worldpay XML 请求或响应原文
     * @return 脱敏后的 XML 摘要
     */
    static String maskWorldPayXml(String xml) {
        if (!StringUtils.hasText(xml)) {
            return xml;
        }
        String masked = XML_PAN_PATTERN.matcher(xml).replaceAll(matchResult -> Matcher.quoteReplacement(
                matchResult.group(1)
                        + matchResult.group(2)
                        + "******"
                        + matchResult.group(4)
                        + matchResult.group(5)
        ));
        masked = XML_SECRET_ELEMENT_PATTERN.matcher(masked).replaceAll("$1***$3");
        masked = XML_EXPIRY_DATE_PATTERN.matcher(masked).replaceAll("$1***$3***$5");
        masked = XML_PERSONAL_ELEMENT_PATTERN.matcher(masked).replaceAll("$1***$3");
        masked = XML_SHOPPER_IP_PATTERN.matcher(masked).replaceAll("$1***$3");
        return masked.replaceAll("(Authorization: Basic )[^\\r\\n]+", "$1***");
    }

    /**
     * 从 XML 响应摘要中解析原始渠道状态。
     * <p>
     * 仅用于响应结束日志，解析失败返回 null，不影响主响应映射流程。
     * </p>
     *
     * @param body Worldpay XML 响应原文
     * @return 原始渠道状态
     */
    private String rawStatus(String body) {
        try {
            return responseMapper.toChannelResponse(null, body).getRawChannelStatus();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 从 XML 响应摘要中解析渠道响应码。
     * <p>
     * 仅用于日志字段 responseCode；解析失败返回 null，主流程仍按 handleResponse 的结果处理。
     * </p>
     *
     * @param body Worldpay XML 响应原文
     * @return 渠道响应码
     */
    private String rawResponseCode(String body) {
        try {
            return responseMapper.toChannelResponse(null, body).getChannelResponseCode();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 从 XML 响应摘要中解析渠道交易号。
     *
     * @param body Worldpay XML 响应原文
     * @return 渠道交易号；解析失败时返回 null
     */
    private String rawChannelTransactionId(String body) {
        try {
            return responseMapper.toChannelResponse(null, body).getChannelTransactionId();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Worldpay XML MID 配置快照。
     *
     * @param merchantCode Worldpay merchantCode，用于 paymentService 根节点
     * @param username Basic Auth 用户名
     * @param password Basic Auth 密码，高敏感配置，仅允许在当前 HTTP 请求内使用
     */
    record WorldPayXmlMidConfig(String merchantCode, String username, String password) {
    }
}
