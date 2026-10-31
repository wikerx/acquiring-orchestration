package com.scott.payment.channel.payout.scottchannela.client;

import com.scott.payment.channel.payout.dto.request.ChannelPayoutCancelRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.exception.PayoutChannelException;
import com.scott.payment.channel.payout.scottchannela.config.ChannelProperties;
import com.scott.payment.channel.payout.scottchannela.converter.PayoutRequestConverter;
import com.scott.payment.channel.payout.scottchannela.converter.PayoutResponseConverter;
import com.scott.payment.channel.payout.scottchannela.model.request.SubmitRequestPayload;
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
 * ScottChannelA 代付 HTTP 客户端，负责凭据解析、URL 组合、超时、发送和脱敏审计。
 *
 * <p>协议请求构造和响应语义解释由独立转换器完成；代付状态推进、幂等和终态保护由代付核心负责。</p>
 **/
public class PayoutApiClient {

    private static final String API_PATH_PREFIX = "/api/";
    private static final String PAYOUT_PATH = "/payouts";
    private static final String QUERY_PATH = "/payouts/query";
    private static final String CANCEL_PATH = "/payouts/cancel";
    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";
    private static final String HEADER_API_KEY = "X-Api-Key";
    private static final String CONTENT_TYPE = "application/json";

    private final ChannelProperties properties;
    private final HttpClient httpClient;
    private final PayoutRequestConverter requestConverter;
    private final PayoutResponseConverter responseConverter;

    /**
     * 创建使用环境级 HTTP 客户端的 ScottChannelA 代付客户端。
     *
     * @param properties 环境级连接配置和兜底凭据
     **/
    public PayoutApiClient(ChannelProperties properties) {
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
    PayoutApiClient(ChannelProperties properties, HttpClient httpClient) {
        this(properties, httpClient, new PayoutRequestConverter(), new PayoutResponseConverter());
    }

    /**
     * 完整注入代付协议依赖，确保测试能够单独验证传输和转换职责。
     **/
    PayoutApiClient(ChannelProperties properties,
                    HttpClient httpClient,
                    PayoutRequestConverter requestConverter,
                    PayoutResponseConverter responseConverter) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.requestConverter = requestConverter;
        this.responseConverter = responseConverter;
    }

    /**
     * 使用平台代付单号作为渠道幂等键提交代付。
     *
     * @param request 平台统一代付请求
     * @return 渠道统一代付响应
     **/
    public ChannelPayoutResponse submit(ChannelPayoutRequest request) {
        SubmitRequestPayload payload = requestConverter.convertSubmit(request);
        return execute(request.getChannelCode(), request.getOperationId(), request.getPayoutOrderNo(),
                PAYOUT_PATH, payload, request.getExtension());
    }

    /**
     * 使用原交易持久化的渠道身份查询代付状态。
     *
     * @param request 平台统一代付查询请求
     * @return 渠道统一代付响应
     **/
    public ChannelPayoutResponse query(ChannelPayoutQueryRequest request) {
        if (request == null) {
            throw new PayoutChannelException("ScottChannelA payout query request is required");
        }
        return execute(request.getChannelCode(), request.getOperationId(), request.getPayoutOrderNo(),
                QUERY_PATH,
                requestConverter.convertOrder(request.getChannelOrderNo(), request.getChannelTransactionId()),
                request.getExtension());
    }

    /**
     * 使用原交易持久化的渠道身份取消仍在处理中的代付。
     *
     * @param request 平台统一代付取消请求
     * @return 渠道统一代付响应
     **/
    public ChannelPayoutResponse cancel(ChannelPayoutCancelRequest request) {
        if (request == null) {
            throw new PayoutChannelException("ScottChannelA payout cancel request is required");
        }
        return execute(request.getChannelCode(), request.getOperationId(), request.getPayoutOrderNo(),
                CANCEL_PATH,
                requestConverter.convertOrder(request.getChannelOrderNo(), request.getChannelTransactionId()),
                request.getExtension());
    }

    /**
     * 使用原交易 MID 凭据调用指定代付端点。
     *
     * <p>超时、网络错误和中断均保留为结果未知，由查询或回调勾兑。</p>
     **/
    private ChannelPayoutResponse execute(String channelCode,
                                          String operationId,
                                          String payoutOrderNo,
                                          String path,
                                          Object payload,
                                          Map<String, String> extension) {
        RequestContext context = resolveRequestContext(path, extension);
        String requestBody = JsonUtils.toJsonString(payload);
        HttpRequest httpRequest = buildHttpRequest(context, requestBody, extension);
        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ChannelPayoutResponse result = responseConverter.convert(
                    channelCode, operationId, payoutOrderNo, response.statusCode(), response.body());
            addAuditEvidence(result, context, requestBody);
            return result;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new PayoutChannelException("ScottChannelA payout request timed out", exception, true);
        } catch (IOException exception) {
            throw new PayoutChannelException("ScottChannelA payout network request failed", exception, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PayoutChannelException("ScottChannelA payout request was interrupted", exception, true);
        }
    }

    /**
     * 解析渠道默认地址、MID 级版本和凭据，并在网络调用前校验。
     **/
    private RequestContext resolveRequestContext(String path, Map<String, String> extension) {
        String baseUrl = firstText(extension.get("requestUrl"), properties.getBaseUrl());
        String version = firstText(extension.get("mid.version"), properties.getVersion());
        String merchantId = firstText(extension.get("mid.merchantId"), extension.get("midNo"),
                properties.getMerchantId());
        String apiKey = firstText(extension.get("mid.apiKey"), properties.getApiKey());
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(version) || !StringUtils.hasText(merchantId)
                || !StringUtils.hasText(apiKey)) {
            throw new PayoutChannelException(
                    "ScottChannelA payout requestUrl, version, merchantId and apiKey are required");
        }
        String normalizedVersion = version.trim().replaceAll("^/+|/+$", "");
        if (!normalizedVersion.matches("v[0-9]+")) {
            throw new PayoutChannelException("ScottChannelA payout version is invalid");
        }
        String url = baseUrl.trim().replaceAll("/+$", "") + API_PATH_PREFIX + normalizedVersion + path;
        return new RequestContext(url, merchantId, apiKey);
    }

    /**
     * 构造只包含协议允许 Header 的 POST 请求。
     **/
    private HttpRequest buildHttpRequest(RequestContext context,
                                         String requestBody,
                                         Map<String, String> extension) {
        return HttpRequest.newBuilder(URI.create(context.url()))
                .timeout(Duration.ofMillis(readTimeoutMillis(extension)))
                .header(HEADER_MERCHANT_ID, context.merchantId())
                .header(HEADER_API_KEY, context.apiKey())
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 保存脱敏后的请求证据，禁止在响应对象中保留 API Key 或收款参数明文。
     **/
    private void addAuditEvidence(ChannelPayoutResponse result,
                                  RequestContext context,
                                  String requestBody) {
        result.setRequestUrlMasked(context.url());
        result.setRequestHeaderJsonMasked(JsonUtils.toJsonString(Map.of(
                HEADER_MERCHANT_ID, SensitiveDataMaskUtils.maskJson(
                        JsonUtils.toJsonString(Map.of("merchantId", context.merchantId()))),
                HEADER_API_KEY, "***")));
        result.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(requestBody));
    }

    /**
     * 读取本次请求超时；非法覆盖值回退到环境配置。
     **/
    private int readTimeoutMillis(Map<String, String> extension) {
        try {
            String seconds = extension.get("readTimeoutSeconds");
            return StringUtils.hasText(seconds)
                    ? Integer.parseInt(seconds) * 1000
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

    /**
     * 本次代付请求的已校验 URL 和敏感凭据，仅在调用栈内存中使用。
     **/
    private record RequestContext(String url, String merchantId, String apiKey) {
    }
}
