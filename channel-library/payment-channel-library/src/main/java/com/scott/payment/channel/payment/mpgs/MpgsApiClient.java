package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
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
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsApiClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS REST API 客户端，位于 payment-channel-library 渠道实现层，负责构造 MPGS URL、Basic Auth、HTTP 调用和脱敏请求响应日志；不处理平台交易状态机。
 * @status : create
 */
@Slf4j
@Component
public class MpgsApiClient {

    /**
     * HTTP METHOD GET 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String HTTP_METHOD_GET = "GET";

    /**
     * HTTP METHOD PUT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String HTTP_METHOD_PUT = "PUT";

    /**
     * EXT REQUEST URL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EXT_REQUEST_URL = "requestUrl";

    /**
     * EXT READ TIMEOUT SECONDS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EXT_READ_TIMEOUT_SECONDS = "readTimeoutSeconds";

    /**
     * EXT MPGS MERCHANT ID 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EXT_MPGS_MERCHANT_ID = "mid.merchantId";

    /**
     * EXT MPGS API USERNAME 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EXT_MPGS_API_USERNAME = "mid.apiUsername";

    /**
     * EXT MPGS PASSWORD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；高敏感字段，禁止打印日志、禁止写入异常消息，持久化前需确认安全要求。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EXT_MPGS_PASSWORD = "mid.password";

    /**
     * EXT MPGS API PASSWORD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；高敏感字段，禁止打印日志、禁止写入异常消息，持久化前需确认安全要求。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EXT_MPGS_API_PASSWORD = "mid.apiPassword";

    /**
     * EXT MPGS API VERSION 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EXT_MPGS_API_VERSION = "mid.version";

    /**
     * RAW HTTP METHOD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RAW_HTTP_METHOD = "httpMethod";

    /**
     * RAW REQUEST URL MASKED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RAW_REQUEST_URL_MASKED = "requestUrlMasked";

    /**
     * RAW REQUEST HEADER JSON MASKED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RAW_REQUEST_HEADER_JSON_MASKED = "requestHeaderJsonMasked";

    /**
     * RAW REQUEST BODY JSON MASKED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RAW_REQUEST_BODY_JSON_MASKED = "requestBodyJsonMasked";

    /**
     * RAW RESPONSE HEADER JSON MASKED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RAW_RESPONSE_HEADER_JSON_MASKED = "responseHeaderJsonMasked";

    /**
     * RAW RESPONSE BODY JSON MASKED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String RAW_RESPONSE_BODY_JSON_MASKED = "responseBodyJsonMasked";

    private static final Pattern MPGS_CARD_NUMBER_PATTERN = Pattern.compile(
            "(\"number\"\\s*:\\s*\")([0-9]{6})([0-9]{1,19})([0-9]{4})(\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MPGS_SECRET_FIELD_PATTERN = Pattern.compile(
            "(\"(?:authenticationToken|apiPassword)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * MPGS 渠道配置，包含网关地址、API 版本、商户号和凭据引用；密码只允许用于组装认证头。
     */
    private final MpgsChannelProperties properties;

    /**
     * 平台统一渠道请求到 MPGS JSON 请求体的映射器。
     */
    private final MpgsRequestMapper requestMapper;

    /**
     * MPGS 原始响应到平台统一渠道响应的映射器。
     */
    private final MpgsResponseMapper responseMapper;

    /**
     * JDK HTTP 客户端，测试环境可注入替身或访问本地 fake server。
     */
    private final HttpClient httpClient;

    /**
     * 创建 MPGS API 客户端。
     *
     * @param properties     MPGS 渠道配置
     * @param requestMapper  MPGS 请求映射器
     * @param responseMapper MPGS 响应映射器
     */
    @Autowired
    public MpgsApiClient(MpgsChannelProperties properties,
                         MpgsRequestMapper requestMapper,
                         MpgsResponseMapper responseMapper) {
        this(properties, requestMapper, responseMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build());
    }

    /**
     * 创建 MPGS API 客户端。
     *
     * @param properties     MPGS 渠道配置
     * @param requestMapper  MPGS 请求映射器
     * @param responseMapper MPGS 响应映射器
     * @param httpClient     HTTP 客户端，测试可注入替身
     */
    MpgsApiClient(MpgsChannelProperties properties,
                  MpgsRequestMapper requestMapper,
                  MpgsResponseMapper responseMapper,
                  HttpClient httpClient) {
        this.properties = properties;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.httpClient = httpClient;
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
                operation = MpgsApiOperation.RETRIEVE;
                logRequest(request, httpMethod, operation, url, null);
                fillRawRequestAudit(request, httpMethod, url, null);
                response = sendGet(request, url);
            } else {
                MpgsRequestPayload payload = requestMapper.toMpgsRequest(request);
                requestBody = JsonUtils.toJsonString(payload);
                httpMethod = HTTP_METHOD_PUT;
                operation = payload.getApiOperation();
                logRequest(request, httpMethod, operation, url, payload);
                fillRawRequestAudit(request, httpMethod, url, requestBody);
                response = sendPut(request, url, requestBody);
            }
            return handleResponse(request, response, httpMethod, operation, url, startNanos);
        } catch (java.net.http.HttpTimeoutException e) {
            logRequestException(request, httpMethod, operation, url, startNanos, e);
            throw new ChannelTimeoutException("MPGS request timed out", e);
        } catch (IOException e) {
            logRequestException(request, httpMethod, operation, url, startNanos, e);
            throw new ChannelRequestException("MPGS network request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logRequestException(request, httpMethod, operation, url, startNanos, e);
            throw new ChannelRequestException("MPGS request was interrupted", e);
        }
    }

    /**
     * 处理 MPGS HTTP 响应并打印脱敏响应日志。
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
        log.info("MPGS渠道响应上下文，context: {}", JsonUtils.toJsonString(new ResponseLogContext(
                httpMethod, operation, request.getOperationId(), request.getTransactionId(),
                request.getChannelOrderNo(), request.getChannelTransactionId(), request.getMerchantOrderNo(),
                response.statusCode(), elapsedMillis(startNanos)
        )));
        log.info("MPGS渠道响应报文，response={}", JsonUtils.toJsonString(toMaskedJsonLogObject(body)));
        if (!StringUtils.hasText(body)) {
            throw new ChannelResponseException("MPGS response body is empty");
        }
        MpgsResponsePayload payload = parseResponseBody(body, response.statusCode());
        if (payload == null) {
            throw new ChannelResponseException("MPGS parsed response is empty");
        }
        if ((response.statusCode() < 200 || response.statusCode() >= 300) && !hasMpgsResult(payload)) {
            throw new ChannelResponseException("MPGS HTTP response is not successful, status: " + response.statusCode());
        }
        ChannelPaymentResponse channelResponse = responseMapper.toChannelResponse(request, payload);
        fillRawResponseAudit(channelResponse, request, response, httpMethod, requestUrl);
        return channelResponse;
    }

    /**
     * 填充 fill Raw Request Audit 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param httpMethod http Method 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestUrl request Url 输入值，含义由调用方法名称和所属业务对象限定
     * @param requestBody request Body 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void fillRawRequestAudit(ChannelPaymentRequest request, String httpMethod, String requestUrl, String requestBody) {
        if (request == null) {
            return;
        }
        request.getExtension().put(RAW_HTTP_METHOD, httpMethod);
        request.getExtension().put(RAW_REQUEST_URL_MASKED, requestUrl);
        request.getExtension().put(RAW_REQUEST_HEADER_JSON_MASKED, JsonUtils.toJsonString(Collections.singletonMap("Authorization", "Basic ***")));
        request.getExtension().put(RAW_REQUEST_BODY_JSON_MASKED, StringUtils.hasText(requestBody)
                ? maskMpgsJson(requestBody)
                : JsonUtils.toJsonString(Collections.emptyMap()));
    }

/**
 * 填充 fill Raw Response Audit 相关字段，保持来源对象与目标对象的业务含义一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param channelResponse channel Response 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @param response response 输入值，含义由调用方法名称和所属业务对象限定
 * @param httpMethod http Method 输入值，含义由调用方法名称和所属业务对象限定
 * @param requestUrl request Url 输入值，含义由调用方法名称和所属业务对象限定
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
        channelResponse.setResponseBodyJsonMasked(maskMpgsJson(response.body()));
        channelResponse.getRawResponse().put("httpStatus", String.valueOf(response.statusCode()));
        putIfText(channelResponse, RAW_HTTP_METHOD, channelResponse.getHttpMethod());
        putIfText(channelResponse, RAW_REQUEST_URL_MASKED, channelResponse.getRequestUrlMasked());
        putIfText(channelResponse, RAW_REQUEST_HEADER_JSON_MASKED, channelResponse.getRequestHeaderJsonMasked());
        putIfText(channelResponse, RAW_REQUEST_BODY_JSON_MASKED, channelResponse.getRequestBodyJsonMasked());
        putIfText(channelResponse, RAW_RESPONSE_HEADER_JSON_MASKED, channelResponse.getResponseHeaderJsonMasked());
        putIfText(channelResponse, RAW_RESPONSE_BODY_JSON_MASKED, channelResponse.getResponseBodyJsonMasked());
    }

    /**
     * 完成 put If Text 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param channelResponse channel Response 输入值，含义由调用方法名称和所属业务对象限定
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     */
    private void putIfText(ChannelPaymentResponse channelResponse, String key, String value) {
        if (channelResponse != null && StringUtils.hasText(value)) {
            channelResponse.getRawResponse().put(key, value);
        }
    }

    /**
     * 完成 audit Value 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String auditValue(ChannelPaymentRequest request, String key) {
        if (request == null || request.getExtension() == null) {
            return null;
        }
        return request.getExtension().get(key);
    }

    /**
     * 解析 parse Response Body 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param body body 输入值，含义由调用方法名称和所属业务对象限定
     * @param httpStatus 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 解析后的内部数据结构或业务值
     */
    private MpgsResponsePayload parseResponseBody(String body, int httpStatus) {
        try {
            return JsonUtils.parseObject(body, MpgsResponsePayload.class);
        } catch (RuntimeException e) {
            if (httpStatus < 200 || httpStatus >= 300) {
                throw new ChannelResponseException("MPGS HTTP response is not successful, status: " + httpStatus, e);
            }
            throw new ChannelResponseException("MPGS response parse failed", e);
        }
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
    private boolean hasMpgsResult(MpgsResponsePayload payload) {
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
        return baseUrl
                + "version/" + encode(extensionValue(request, EXT_MPGS_API_VERSION, properties.getVersion()))
                + "/merchant/" + encode(extensionValue(request, EXT_MPGS_MERCHANT_ID, properties.getMerchantId()))
                + "/order/" + encode(request.getChannelOrderNo())
                + "/transaction/" + encode(request.getChannelTransactionId());
    }

    /**
     * 构造 MPGS Basic Auth 请求头。该值不得写入日志。
     *
     * @return Basic Auth 请求头
     */
    private String basicAuthHeader(ChannelPaymentRequest request) {
        String merchantId = extensionValue(request, EXT_MPGS_MERCHANT_ID, properties.getMerchantId());
        String username = extensionValue(request, EXT_MPGS_API_USERNAME, properties.getApiUsername());
        if (!StringUtils.hasText(username)) {
            username = "merchant." + merchantId;
        }
        String raw = username + ":" + mpgsPassword(request);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 校验 MPGS 渠道配置并补齐默认用户名。
     */
    private void validateProperties(ChannelPaymentRequest request) {
        if (!properties.isEnabled()) {
            throw new ChannelRequestException("MPGS live channel is disabled");
        }
        requireText(extensionValue(request, EXT_REQUEST_URL, properties.getBaseUrl()), "MPGS baseUrl is required");
        requireText(extensionValue(request, EXT_MPGS_API_VERSION, properties.getVersion()), "MPGS version is required");
        requireText(extensionValue(request, EXT_MPGS_MERCHANT_ID, properties.getMerchantId()), "MPGS merchantId is required");
        requireText(mpgsPassword(request), "MPGS password is required");
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
     * 打印 MPGS 脱敏请求日志。
     *
     * @param request     渠道统一请求
     * @param httpMethod  HTTP 方法
     * @param operation   MPGS API 操作
     * @param url         MPGS 请求 URL
     * @param payload     MPGS 请求对象，GET 查询为空
     */
    private void logRequest(ChannelPaymentRequest request,
                            String httpMethod,
                            String operation,
                            String url,
                            MpgsRequestPayload payload) {
        log.info("MPGS渠道请求上下文，context: {}", JsonUtils.toJsonString(new RequestLogContext(
                httpMethod, operation, url, request.getOperationId(), request.getTransactionId(),
                request.getChannelOrderNo(), request.getChannelTransactionId(),
                request.getMerchantId(), request.getMerchantOrderNo(), request.getMerchantOrderId(), request.getTransactionType(),
                String.valueOf(request.getAmount()), request.getCurrency()
        )));
        log.info("MPGS渠道请求报文，request={}", JsonUtils.toJsonString(toMaskedJsonLogObject(payload)));
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
        log.warn("MPGS渠道请求异常，method: {}, operation: {}, url: {}, operationId: {}, transactionId: {}, "
                        + "channelOrderNo: {}, channelTransactionId: {}, merchantOrderNo: {}, durationMillis: {}, errorType: {}, errorMessage: {}",
                httpMethod, operation, url, safeOperationId(request), safeTransactionId(request),
                safeChannelOrderNo(request), safeChannelTransactionId(request), safeMerchantOrderNo(request),
                elapsedMillis(startNanos), exception.getClass().getSimpleName(),
                exception.getMessage(), exception);
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
     * 标准化 normalize Type 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return 标准化后的业务字段值
     */
    private String normalizeType(String transactionType) {
        return transactionType == null ? null : transactionType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 完成 encode 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 强制校验 require Text 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     */
    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ChannelRequestException(message);
        }
    }

    /**
     * 完成 read Timeout 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 extension Value 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @param fallback fallback 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String extensionValue(ChannelPaymentRequest request, String key, String fallback) {
        if (request != null && request.getExtension() != null && StringUtils.hasText(request.getExtension().get(key))) {
            return request.getExtension().get(key);
        }
        return fallback;
    }

    /**
     * 完成 first Text 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param values values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 safe Operation Id 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private String safeOperationId(ChannelPaymentRequest request) {
        return request == null ? null : request.getOperationId();
    }

    /**
     * 完成 safe Transaction Id 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    private String safeTransactionId(ChannelPaymentRequest request) {
        return request == null ? null : request.getTransactionId();
    }

    /**
     * 完成 safe Channel Order No 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    private String safeChannelOrderNo(ChannelPaymentRequest request) {
        return request == null ? null : request.getChannelOrderNo();
    }

    /**
     * 完成 safe Channel Transaction Id 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    private String safeChannelTransactionId(ChannelPaymentRequest request) {
        return request == null ? null : request.getChannelTransactionId();
    }

    /**
     * 完成 safe Merchant Order No 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    private String safeMerchantOrderNo(ChannelPaymentRequest request) {
        return request == null ? null : request.getMerchantOrderNo();
    }

    /**
     * 对 MPGS JSON 请求/响应执行脱敏。
     * <p>
     * 该方法会先复用全局 JSON 脱敏工具，再补充 MPGS 特有字段：sourceOfFunds.provided.card.number 和
     * authentication.threeDs.authenticationToken。测试日志、生产日志和断言都应复用此方法，避免多套脱敏规则。
     *
     * @param json MPGS 原始 JSON
     * @return 脱敏后的 JSON
     */
    static String maskMpgsJson(String json) {
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
        return MPGS_SECRET_FIELD_PATTERN.matcher(masked).replaceAll("$1***$3");
    }

    /**
     * 将请求对象转为可直接复制的脱敏 JSON 日志对象。
     *
     * @param payload MPGS 请求对象，查询接口为空
     * @return 可序列化的脱敏 JSON 对象
     */
    private Object toMaskedJsonLogObject(MpgsRequestPayload payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        return toMaskedJsonLogObject(JsonUtils.toJsonString(payload));
    }

    /**
     * 将原始 JSON 字符串转为可直接复制的脱敏 JSON 日志对象。
     * <p>
     * MPGS HTTP 响应进入系统时先是字符串，这里先脱敏再解析为对象，避免日志出现嵌套 JSON 字符串和反斜杠转义。
     *
     * @param json 原始 JSON 字符串
     * @return 可序列化的脱敏 JSON 对象；非 JSON 内容以脱敏字符串返回
     */
    private Object toMaskedJsonLogObject(String json) {
        String masked = maskMpgsJson(json);
        if (!StringUtils.hasText(masked)) {
            return Collections.emptyMap();
        }
        try {
            return JsonUtils.parseObject(masked, Object.class);
        } catch (RuntimeException e) {
            return masked;
        }
    }

    private record RequestLogContext(String method,
                                     String operation,
                                     String url,
                                     String operationId,
                                     String transactionId,
                                     String channelOrderNo,
                                     String channelTransactionId,
                                     String merchantId,
                                     String merchantOrderNo,
                                     String merchantOrderId,
                                     String transactionType,
                                     String amount,
                                     String currency) {
    }

    private record ResponseLogContext(String method,
                                      String operation,
                                      String operationId,
                                      String transactionId,
                                      String channelOrderNo,
                                      String channelTransactionId,
                                      String merchantOrderNo,
                                      int httpStatus,
                                      long durationMillis) {
    }
}
