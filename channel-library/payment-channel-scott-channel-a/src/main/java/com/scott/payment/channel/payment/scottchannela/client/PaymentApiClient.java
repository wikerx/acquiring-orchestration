package com.scott.payment.channel.payment.scottchannela.client;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.channel.payment.scottchannela.config.ChannelProperties;
import com.scott.payment.channel.payment.scottchannela.converter.PaymentRequestConverter;
import com.scott.payment.channel.payment.scottchannela.converter.PaymentResponseConverter;
import com.scott.payment.channel.payment.scottchannela.converter.PreparedPaymentRequest;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * ScottChannelA 收单 HTTP 客户端，负责凭据解析、URL 组合、超时、发送和脱敏审计。
 *
 * <p>协议请求构造和响应语义解释由独立转换器完成；交易状态推进、幂等和终态保护由支付核心负责。</p>
 **/
public class PaymentApiClient {

    /**
     * {@code API_PATH_PREFIX}，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String API_PATH_PREFIX = "/api/";
    /**
     * 请求头商户ID，用于定位 支付API内部调用 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";
    /**
     * 请求头API密钥，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String HEADER_API_KEY = "X-Api-Key";
    /**
     * 内容类型，用于区分 支付API内部调用 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
    private static final String CONTENT_TYPE = "application/json";

    private final ChannelProperties properties;
    private final HttpClient httpClient;
    private final PaymentRequestConverter requestConverter;
    private final PaymentResponseConverter responseConverter;

    /**
     * 创建使用环境级 HTTP 客户端的 ScottChannelA 收单客户端。
     *
     * @param properties 环境级连接配置和兜底凭据
     **/
    public PaymentApiClient(ChannelProperties properties) {
        this(properties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                        .build());
    }

    /**
     * 创建使用指定 HTTP 客户端的实例，供测试注入可控传输层。
     *
     * @param properties 环境级连接配置和兜底凭据
     * @param httpClient HTTP 传输实现
     **/
    PaymentApiClient(ChannelProperties properties, HttpClient httpClient) {
        this(properties, httpClient, new PaymentRequestConverter(), new PaymentResponseConverter());
    }

    /**
     * 完整注入收单协议依赖，确保测试能够单独验证传输和转换职责。
     **/
    PaymentApiClient(ChannelProperties properties,
                     HttpClient httpClient,
                     PaymentRequestConverter requestConverter,
                     PaymentResponseConverter responseConverter) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.requestConverter = requestConverter;
        this.responseConverter = responseConverter;
    }

    /**
     * 执行代收、退款、查询或卡授权生命周期请求。
     *
     * @param request 平台统一收单请求；敏感支付数据只在本次调用内存中使用
     * @return 包含渠道原始证据和脱敏审计快照的标准响应
     * @throws ChannelRequestException 请求、凭据或网络调用无效时抛出
     * @throws ChannelTimeoutException 渠道请求超时时抛出，此时资金结果未知
     * @throws ChannelResponseException 渠道响应无法安全解释时抛出
     **/
    public ChannelPaymentResponse execute(ChannelPaymentRequest request) {
        PreparedPaymentRequest preparedRequest = requestConverter.convert(request);
        String body = JsonUtils.toJsonString(preparedRequest.payload());
        String url = buildUrl(request, preparedRequest.path());
        String merchantId = requiredValue(request,
                new String[]{"mid.merchantId", "mid.merchant_id", "mid.channelMid", "mid.mid", "midNo"},
                properties.getMerchantId());
        String apiKey = requiredValue(request, new String[]{"mid.apiKey"}, properties.getApiKey());
        HttpRequest httpRequest = buildHttpRequest(request, url, merchantId, apiKey, body);
        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ChannelPaymentResponse result = responseConverter.convert(
                    request, response.statusCode(), response.body());
            addAuditEvidence(result, url, merchantId, body, response.body());
            return result;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ChannelTimeoutException("ScottChannelA request timed out", exception);
        } catch (IOException exception) {
            throw new ChannelRequestException("ScottChannelA network request failed", exception, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChannelRequestException("ScottChannelA request was interrupted", exception, true);
        }
    }

    /**
     * 构造只包含协议允许 Header 的 POST 请求。
     **/
    private HttpRequest buildHttpRequest(ChannelPaymentRequest request,
                                         String url,
                                         String merchantId,
                                         String apiKey,
                                         String body) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMillis(request)))
                .header(HEADER_MERCHANT_ID, merchantId)
                .header(HEADER_API_KEY, apiKey)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 组合渠道默认请求地址、MID 级 API 版本和操作端点。
     *
     * <p>基础地址来自渠道配置；MID 仅覆盖凭据和版本等账号级参数。</p>
     **/
    private String buildUrl(ChannelPaymentRequest request, String path) {
        String baseUrl = firstText(request.getExtension().get("requestUrl"), properties.getBaseUrl());
        String version = firstText(request.getExtension().get("mid.version"), properties.getVersion());
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(version)) {
            throw new ChannelRequestException("ScottChannelA requestUrl and version are required");
        }
        String normalizedBase = baseUrl.trim().replaceAll("/+$", "");
        String normalizedVersion = version.trim().replaceAll("^/+|/+$", "");
        if (!normalizedVersion.matches("v[0-9]+")) {
            throw new ChannelRequestException("ScottChannelA version is invalid");
        }
        return normalizedBase + API_PATH_PREFIX + normalizedVersion + path;
    }

    /**
     * 保存脱敏后的请求和响应证据，禁止在响应对象中保留 API Key、PAN 或安全码明文。
     **/
    private void addAuditEvidence(ChannelPaymentResponse result,
                                  String url,
                                  String merchantId,
                                  String requestBody,
                                  String responseBody) {
        result.setHttpMethod("POST");
        result.setRequestUrlMasked(url);
        result.setRequestHeaderJsonMasked(JsonUtils.toJsonString(Map.of(
                HEADER_MERCHANT_ID, SensitiveDataMaskUtils.maskJson(
                        JsonUtils.toJsonString(Map.of("merchantId", merchantId))),
                HEADER_API_KEY, "***",
                "Content-Type", CONTENT_TYPE)));
        result.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(requestBody));
        result.setResponseBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(responseBody));
    }

    /**
     * 解析 MID 或环境级必填配置，缺失时在网络调用前拒绝。
     **/
    private String requiredValue(ChannelPaymentRequest request, String[] keys, String fallback) {
        for (String key : keys) {
            String value = request.getExtension().get(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        if (StringUtils.hasText(fallback)) {
            return fallback;
        }
        throw new ChannelRequestException("ScottChannelA credential is not configured");
    }

    /**
     * 读取本次请求超时；非法覆盖值回退到环境配置。
     **/
    private int readTimeoutMillis(ChannelPaymentRequest request) {
        String configured = request.getExtension().get("readTimeoutSeconds");
        try {
            return StringUtils.hasText(configured)
                    ? Integer.parseInt(configured) * 1000
                    : properties.getReadTimeoutMillis();
        } catch (NumberFormatException ignored) {
            return properties.getReadTimeoutMillis();
        }
    }

    private String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }
}
