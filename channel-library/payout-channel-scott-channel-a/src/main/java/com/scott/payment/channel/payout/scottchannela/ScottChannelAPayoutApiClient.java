package com.scott.payment.channel.payout.scottchannela;

import com.scott.payment.channel.payout.dto.request.ChannelPayoutCancelRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.enums.PayoutChannelStatus;
import com.scott.payment.channel.payout.exception.PayoutChannelException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAPayoutApiClient
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 代付 JSON 客户端，负责 submit/query/cancel 三个 POST 接口和渠道状态映射，不直接推进 service-payout 状态机。
 * @status : create
 */
public class ScottChannelAPayoutApiClient {

    private static final String API_PATH_PREFIX = "/api/";
    private static final String PAYOUT_PATH = "/payouts";
    private static final String QUERY_PATH = "/payouts/query";
    private static final String CANCEL_PATH = "/payouts/cancel";
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ScottChannelAPayoutProperties properties;
    private final HttpClient httpClient;

    /**
     * 创建使用环境级 HTTP 客户端的 ScottChannelA 代付 API 客户端。
     *
     * @param properties ScottChannelA 代付连接配置和兜底凭据
     */
    public ScottChannelAPayoutApiClient(ScottChannelAPayoutProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build());
    }

    ScottChannelAPayoutApiClient(ScottChannelAPayoutProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /**
     * 提交 ScottChannelA 代付。
     *
     * @param request 平台统一代付请求
     * @return 渠道统一代付响应
     */
    public ChannelPayoutResponse submit(ChannelPayoutRequest request) {
        return execute(request, PAYOUT_PATH, buildSubmitPayload(request));
    }

    /**
     * 查询 ScottChannelA 代付。
     *
     * @param request 平台统一代付查询请求
     * @return 渠道统一代付响应
     */
    public ChannelPayoutResponse query(ChannelPayoutQueryRequest request) {
        if (request == null) {
            throw new PayoutChannelException("ScottChannelA payout query request is required");
        }
        Map<String, Object> payload = buildIdentifierPayload(request.getChannelOrderNo(), request.getChannelTransactionId());
        return execute(request.getChannelCode(), request.getOperationId(), request.getPayoutOrderNo(), QUERY_PATH, payload,
                request.getExtension());
    }

    /**
     * 取消仍在 ScottChannelA 处理中状态的代付。
     *
     * @param request 平台统一代付取消请求
     * @return 渠道统一代付响应
     */
    public ChannelPayoutResponse cancel(ChannelPayoutCancelRequest request) {
        if (request == null) {
            throw new PayoutChannelException("ScottChannelA payout cancel request is required");
        }
        Map<String, Object> payload = buildIdentifierPayload(request.getChannelOrderNo(), request.getChannelTransactionId());
        return execute(request.getChannelCode(), request.getOperationId(), request.getPayoutOrderNo(), CANCEL_PATH, payload,
                request.getExtension());
    }

    private Map<String, Object> buildSubmitPayload(ChannelPayoutRequest request) {
        if (request == null || !StringUtils.hasText(request.getMerchantOrderNo()) || request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0 || !StringUtils.hasText(request.getCurrency())
                || !StringUtils.hasText(request.getBeneficiaryReference())) {
            throw new PayoutChannelException("ScottChannelA payout merchantOrderNo, amount, currency and beneficiary are required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantOrderNo", request.getMerchantOrderNo());
        payload.put("amount", request.getAmount());
        payload.put("currency", request.getCurrency().trim().toUpperCase(Locale.ROOT));
        String paymentMethod = firstText(request.getPaymentMethod(), request.getExtension().get("paymentMethod"));
        if (!StringUtils.hasText(paymentMethod)) {
            throw new PayoutChannelException("ScottChannelA payout paymentMethod is required");
        }
        payload.put("paymentMethod", resolveChannelPaymentMethod(paymentMethod));
        putIfText(payload, "notifyUrl", request.getExtension().get("callbackUrl"));
        putIfText(payload, "clientIp", request.getExtension().get("clientIp"));
        payload.put("requestTime", LocalDateTime.now().format(REQUEST_TIME_FORMATTER));
        Map<String, Object> paymentData = parsePaymentData(request.getExtension().get("paymentData"));
        if (paymentData.isEmpty()) {
            paymentData = new LinkedHashMap<>();
            paymentData.put("beneficiaryReference", request.getBeneficiaryReference());
        }
        payload.put("paymentData", paymentData);
        return payload;
    }

    private ChannelPayoutResponse execute(ChannelPayoutRequest request, String path, Map<String, Object> payload) {
        return execute(request.getChannelCode(), request.getOperationId(), request.getPayoutOrderNo(), path, payload,
                request.getExtension());
    }

    private ChannelPayoutResponse execute(String channelCode, String operationId, String payoutOrderNo, String path,
                                          Map<String, Object> payload, Map<String, String> extension) {
        // 渠道基础地址来自 channel_info.default_request_url，MID 只覆盖凭据和版本等账号级参数。
        String baseUrl = firstText(extension.get("requestUrl"), properties.getBaseUrl());
        String version = firstText(extension.get("mid.version"), properties.getVersion());
        String merchantId = firstText(extension.get("mid.merchantId"), extension.get("midNo"), properties.getMerchantId());
        String apiKey = firstText(extension.get("mid.apiKey"), properties.getApiKey());
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(version) || !StringUtils.hasText(merchantId)
                || !StringUtils.hasText(apiKey)) {
            throw new PayoutChannelException("ScottChannelA payout requestUrl, version, merchantId and apiKey are required");
        }
        String normalizedVersion = version.trim().replaceAll("^/+|/+$", "");
        if (!normalizedVersion.matches("v[0-9]+")) {
            throw new PayoutChannelException("ScottChannelA payout version is invalid");
        }
        String url = baseUrl.trim().replaceAll("/+$", "") + API_PATH_PREFIX + normalizedVersion + path;
        String requestBody = JsonUtils.toJsonString(payload);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMillis(extension)))
                .header("X-Merchant-Id", merchantId)
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ChannelPayoutResponse result = mapResponse(channelCode, operationId, payoutOrderNo, response.statusCode(), response.body());
            result.setHttpStatus(response.statusCode());
            result.setRequestUrlMasked(url);
            result.setRequestHeaderJsonMasked(JsonUtils.toJsonString(Map.of(
                    "X-Merchant-Id", SensitiveDataMaskUtils.maskJson(
                            JsonUtils.toJsonString(Map.of("merchantId", merchantId))),
                    "X-Api-Key", "***")));
            result.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(requestBody));
            return result;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new PayoutChannelException("ScottChannelA payout request timed out", exception);
        } catch (IOException exception) {
            throw new PayoutChannelException("ScottChannelA payout network request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PayoutChannelException("ScottChannelA payout request was interrupted", exception);
        }
    }

    private ChannelPayoutResponse mapResponse(String channelCode, String operationId, String payoutOrderNo,
                                              int httpStatus, String responseBody) {
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new PayoutChannelException("ScottChannelA payout returned HTTP status " + httpStatus);
        }
        Map<String, Object> root = parseResponseMap(responseBody);
        Map<String, Object> data = mapValue(root.get("data"));
        ChannelPayoutResponse result = new ChannelPayoutResponse();
        result.setChannelCode(channelCode);
        result.setOperationId(operationId);
        result.setPayoutOrderNo(payoutOrderNo);
        result.setChannelOrderNo(text(data, "merchantOrderNo"));
        result.setChannelTransactionId(text(data, "transactionId"));
        String status = firstText(text(data, "status"), text(root, "code"));
        result.setRawChannelStatus(status);
        result.setChannelPayoutStatus(mapStatus(status).getCode());
        result.setChannelResponseCode(firstText(text(data, "responseCode"), text(root, "code")));
        result.setChannelResponseMessage(firstText(text(data, "responseDescription"), text(root, "message")));
        result.setChannelCurrency(text(data, "currency"));
        result.setChannelAmount(decimal(data, "amount"));
        result.getRawResponse().put("status", status);
        result.getRawResponse().put("responseCode", result.getChannelResponseCode());
        result.getRawResponse().put("responseDescription", result.getChannelResponseMessage());
        return result;
    }

    private PayoutChannelStatus mapStatus(String status) {
        if (status == null) {
            return PayoutChannelStatus.PROCESSING;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED" -> PayoutChannelStatus.SUCCESS;
            case "FAIL", "FAILED" -> PayoutChannelStatus.FAILED;
            case "CANCELLED", "CANCELED" -> PayoutChannelStatus.RETURNED;
            case "PROCESSING", "PENDING" -> PayoutChannelStatus.PROCESSING;
            default -> PayoutChannelStatus.PENDING;
        };
    }

    /** 渠道查询和取消要求 merchantOrderNo 与 transactionId 二选一。 */
    private Map<String, Object> buildIdentifierPayload(String channelOrderNo, String channelTransactionId) {
        boolean hasOrderNo = StringUtils.hasText(channelOrderNo);
        boolean hasTransactionId = StringUtils.hasText(channelTransactionId);
        if (hasOrderNo == hasTransactionId) {
            throw new PayoutChannelException("ScottChannelA payout requires exactly one order identifier");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (hasOrderNo) {
            payload.put("merchantOrderNo", channelOrderNo);
        } else {
            payload.put("transactionId", channelTransactionId);
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponseMap(String json) {
        if (!StringUtils.hasText(json)) {
            throw new PayoutChannelException("ScottChannelA payout response is empty");
        }
        Map<String, Object> value = JsonUtils.parseObject(json, Map.class);
        if (value == null) {
            throw new PayoutChannelException("ScottChannelA payout response is invalid");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, Object> parsePaymentData(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        return mapValue(JsonUtils.parseObject(value, Map.class));
    }

    private int readTimeoutMillis(Map<String, String> extension) {
        try {
            String seconds = extension.get("readTimeoutSeconds");
            return StringUtils.hasText(seconds) ? Integer.parseInt(seconds) * 1000 : properties.getReadTimeoutMillis();
        } catch (NumberFormatException ignored) {
            return properties.getReadTimeoutMillis();
        }
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimal(Map<String, Object> values, String key) {
        String value = text(values, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void putIfText(Map<String, Object> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value);
        }
    }

    private String resolveChannelPaymentMethod(String paymentMethod) {
        String normalized = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BANK_CARD" -> "CARD";
            case "PAYPAL" -> "PAY_PAL";
            case "CASH_APP_PAY", "CASH_APP", "CASHAPP" -> "CASHAPP";
            default -> normalized;
        };
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
