package com.scott.payment.channel.payment.scottchannela;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * @classname : ScottChannelAApiClient
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA JSON API 客户端，负责构造上游 POST 请求、使用 MID 凭据鉴权和把渠道响应映射为统一收单响应；不推进平台交易状态机。
 * @status : create
 */
@Slf4j
public class ScottChannelAApiClient {

    private static final String API_PATH_PREFIX = "/api/";
    private static final String PAYIN_DIRECT_PATH = "/payins/direct";
    private static final String PAYIN_QUERY_PATH = "/payins/query";
    private static final String REFUND_PATH = "/payins/refunds";
    private static final String REFUND_QUERY_PATH = "/payins/refunds/query";
    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";
    private static final String HEADER_API_KEY = "X-Api-Key";
    private static final String CONTENT_TYPE = "application/json";
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ScottChannelAProperties properties;
    private final HttpClient httpClient;

    /**
     * 创建使用环境级 HTTP 客户端的 ScottChannelA API 客户端。
     *
     * @param properties ScottChannelA 环境级连接配置和兜底凭据
     */
    public ScottChannelAApiClient(ScottChannelAProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build());
    }

    ScottChannelAApiClient(ScottChannelAProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /**
     * 执行收款、查询或退款请求。
     *
     * @param request 平台统一收单请求；敏感支付数据只在本次调用内存中使用
     * @return 渠道统一收单响应
     */
    public ChannelPaymentResponse execute(ChannelPaymentRequest request) {
        validateRequest(request);
        String operation = normalize(request.getTransactionType());
        String path = resolvePath(operation, request);
        Map<String, Object> payload = buildPayload(request, operation);
        String body = JsonUtils.toJsonString(payload);
        String url = buildUrl(request, path);
        String merchantId = requiredValue(request, "mid.merchantId", "mid.merchant_id", "mid.channelMid", "mid.mid", "midNo", properties.getMerchantId());
        String apiKey = requiredValue(request, "mid.apiKey", properties.getApiKey());
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMillis(request)))
                .header(HEADER_MERCHANT_ID, merchantId)
                .header(HEADER_API_KEY, apiKey)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ChannelPaymentResponse result = mapResponse(request, response.statusCode(), response.body());
            result.setHttpMethod("POST");
            result.setRequestUrlMasked(url);
            result.setRequestHeaderJsonMasked(JsonUtils.toJsonString(Map.of(
                    HEADER_MERCHANT_ID, SensitiveDataMaskUtils.maskJson(JsonUtils.toJsonString(Map.of("merchantId", merchantId))),
                    HEADER_API_KEY, "***",
                    "Content-Type", CONTENT_TYPE)));
            result.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(body));
            result.setResponseBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(response.body()));
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

    private ChannelPaymentResponse mapResponse(ChannelPaymentRequest request, int httpStatus, String responseBody) {
        Map<String, Object> root = parseObject(responseBody);
        Map<String, Object> data = mapValue(root.get("data"));
        String responseCode = text(data, "responseCode", text(root, "code", null));
        boolean successfulHttpStatus = httpStatus >= 200 && httpStatus < 300;
        boolean deterministicRejection = httpStatus >= 400 && httpStatus < 500;
        if (!successfulHttpStatus && !deterministicRejection) {
            throw new ChannelResponseException(
                    "ScottChannelA returned HTTP status " + httpStatus, null, true);
        }
        ChannelPaymentResponse result = new ChannelPaymentResponse();
        result.setChannelCode(ScottChannelACode.CODE);
        result.setOperationId(request.getOperationId());
        result.setTransactionId(request.getTransactionId());
        result.setChannelOrderNo(text(data, "merchantOrderNo", request.getChannelOrderNo()));
        boolean refundResponse = ChannelCapability.REFUND.getCode().equals(normalize(request.getTransactionType()))
                || (ChannelCapability.QUERY.getCode().equals(normalize(request.getTransactionType()))
                && isRefundQuery(request));
        result.setChannelTransactionId(refundResponse
                ? text(data, "refundId", request.getChannelTransactionId())
                : text(data, "transactionId", request.getChannelTransactionId()));
        String status = deterministicRejection
                ? firstText(responseCode, "FAILED")
                : text(data, "status", text(root, "code", "PROCESSING"));
        result.setRawChannelStatus(status);
        result.setChannelTradeStatus(deterministicRejection
                ? ChannelTradeStatus.FAILED.getCode()
                : mapStatus(status).getCode());
        result.setChannelResponseCode(responseCode);
        result.setChannelResponseMessage(text(data, "responseDescription", text(root, "message", null)));
        result.setChannelCurrency(text(data, "currency", request.getCurrency()));
        result.setChannelAmount(decimal(data, "amount", request.getAmount()));
        result.setRedirectUrl(text(data, "checkoutUrl", null));
        result.setHttpStatus(httpStatus);
        result.getRawResponse().put("status", status);
        result.getRawResponse().put("responseCode", result.getChannelResponseCode());
        result.getRawResponse().put("responseDescription", result.getChannelResponseMessage());
        putRawResponseIfText(result.getRawResponse(), "merchantRefundNo", text(data, "merchantRefundNo", null));
        putRawResponseIfText(result.getRawResponse(), "refundId", text(data, "refundId", null));
        return result;
    }

    private Map<String, Object> buildPayload(ChannelPaymentRequest request, String operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (ChannelCapability.REFUND.getCode().equals(operation)) {
            putRefundOrderIdentity(payload, request);
            String merchantRefundNo = firstText(request.getExtension().get("merchantRefundNo"),
                    request.getMerchantOrderId());
            if (!StringUtils.hasText(merchantRefundNo)) {
                throw new ChannelRequestException("ScottChannelA merchantRefundNo is required for refunds");
            }
            payload.put("merchantRefundNo", merchantRefundNo);
        } else if (ChannelCapability.QUERY.getCode().equals(operation)) {
            payload.clear();
            if (isRefundQuery(request)) {
                putIfText(payload, "merchantRefundNo", request.getExtension().get("merchantRefundNo"));
                putIfText(payload, "refundId", request.getExtension().get("refundId"));
                if (payload.size() != 1) {
                    throw new ChannelRequestException("ScottChannelA refund query requires exactly one identifier");
                }
            } else {
                putSingleOrderIdentity(payload, request);
            }
        } else {
            String merchantOrderNo = firstText(request.getTransactionId(), request.getChannelOrderNo(),
                    request.getMerchantOrderNo());
            if (!StringUtils.hasText(merchantOrderNo)) {
                throw new ChannelRequestException("ScottChannelA merchantOrderNo is required for payins");
            }
            payload.put("merchantOrderNo", merchantOrderNo);
            payload.put("amount", normalizeAmount(request.getAmount()));
            payload.put("currency", upper(request.getCurrency()));
            payload.put("paymentMethod", resolveChannelPaymentMethod(request.getPaymentMethod()));
            putIfText(payload, "notifyUrl", request.getExtension().get("callbackUrl"));
            putIfText(payload, "returnUrl", request.getExtension().get("returnUrl"));
            putIfText(payload, "clientIp", request.getExtension().get("payerIp"));
            payload.put("requestTime", request.getTransactionDateTime() == null
                    ? LocalDateTime.now().format(REQUEST_TIME_FORMATTER)
                    : request.getTransactionDateTime().format(REQUEST_TIME_FORMATTER));
            addPaymentData(payload, request);
        }
        if (ChannelCapability.REFUND.getCode().equals(operation)) {
            payload.put("amount", normalizeAmount(request.getAmount()));
            putIfText(payload, "notifyUrl", request.getExtension().get("callbackUrl"));
        }
        return payload;
    }

    private void addPaymentData(Map<String, Object> payload, ChannelPaymentRequest request) {
        String json = request.getExtension().get("paymentData");
        Map<String, Object> paymentData = StringUtils.hasText(json)
                ? mapValue(JsonUtils.parseObject(json, Map.class))
                : new LinkedHashMap<>();
        putIfText(paymentData, "cardNo", request.getCardNo());
        putIfText(paymentData, "expirationMonth", request.getExpirationMonth());
        putIfText(paymentData, "expirationYear", request.getExpirationYear());
        putIfText(paymentData, "securityCode", request.getSecurityCode());
        putIfText(paymentData, "cardholderName", request.getCardholderName());
        if (request.getBillingInfo() != null) {
            putIfText(paymentData, "billingFirstName", request.getBillingInfo().getFirstName());
            putIfText(paymentData, "billingLastName", request.getBillingInfo().getLastName());
            putIfText(paymentData, "billingEmail", request.getBillingInfo().getEmail());
            putIfText(paymentData, "billingPhone", request.getBillingInfo().getPhone());
            putIfText(paymentData, "billingCountry", request.getBillingInfo().getCountry());
            putIfText(paymentData, "billingState", request.getBillingInfo().getState());
            putIfText(paymentData, "billingCity", request.getBillingInfo().getCity());
            putIfText(paymentData, "billingStreet", request.getBillingInfo().getStreet());
            putIfText(paymentData, "billingPostal", request.getBillingInfo().getPostal());
        }
        if (!paymentData.isEmpty()) {
            payload.put("paymentData", paymentData);
        }
    }

    private String resolvePath(String operation, ChannelPaymentRequest request) {
        if (ChannelCapability.REFUND.getCode().equals(operation)) {
            return REFUND_PATH;
        }
        if (ChannelCapability.QUERY.getCode().equals(operation)) {
            return isRefundQuery(request) ? REFUND_QUERY_PATH : PAYIN_QUERY_PATH;
        }
        return PAYIN_DIRECT_PATH;
    }

    /** 渠道查询接口要求 merchantOrderNo 与 transactionId 二选一。 */
    private void putSingleOrderIdentity(Map<String, Object> payload, ChannelPaymentRequest request) {
        String transactionId = firstText(request.getChannelTransactionId(), request.getSourceTransactionId());
        if (StringUtils.hasText(transactionId)) {
            payload.put("transactionId", transactionId);
            return;
        }
        String merchantOrderNo = firstText(request.getMerchantOrderNo(), request.getChannelOrderNo());
        if (!StringUtils.hasText(merchantOrderNo)) {
            throw new ChannelRequestException("ScottChannelA order query requires merchantOrderNo or transactionId");
        }
        payload.put("merchantOrderNo", merchantOrderNo);
    }

    /**
     * 渠道退款接口要求引用原代收交易，不能使用退款动作新生成的渠道交易 ID。
     * 优先使用原支付真实渠道交易号，再回退到已持久化的渠道身份，最后才使用商户订单号。
     */
    private void putRefundOrderIdentity(Map<String, Object> payload, ChannelPaymentRequest request) {
        String sourceTransactionId = firstText(request.getExtension().get("targetTransactionId"),
                request.getChannelTransactionId(), request.getChannelOrderNo());
        if (StringUtils.hasText(sourceTransactionId)) {
            payload.put("transactionId", sourceTransactionId);
            return;
        }
        String merchantOrderNo = request.getMerchantOrderNo();
        if (!StringUtils.hasText(merchantOrderNo)) {
            throw new ChannelRequestException("ScottChannelA refund requires the original payment identity");
        }
        payload.put("merchantOrderNo", merchantOrderNo);
    }

    private boolean isRefundQuery(ChannelPaymentRequest request) {
        return request.getExtension().containsKey("refundId")
                || request.getExtension().containsKey("merchantRefundNo");
    }

    private String buildUrl(ChannelPaymentRequest request, String path) {
        // 渠道基础地址来自 channel_info.default_request_url，MID 只覆盖凭据和版本等账号级参数。
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

    private int readTimeoutMillis(ChannelPaymentRequest request) {
        String configured = request.getExtension().get("readTimeoutSeconds");
        try {
            return StringUtils.hasText(configured) ? Integer.parseInt(configured) * 1000 : properties.getReadTimeoutMillis();
        } catch (NumberFormatException ignored) {
            return properties.getReadTimeoutMillis();
        }
    }

    private void validateRequest(ChannelPaymentRequest request) {
        if (request == null || !StringUtils.hasText(request.getTransactionType())) {
            throw new ChannelRequestException("ScottChannelA transactionType is required");
        }
        String operation = normalize(request.getTransactionType());
        boolean query = ChannelCapability.QUERY.getCode().equals(operation);
        boolean refund = ChannelCapability.REFUND.getCode().equals(operation);
        boolean hasOrderIdentity = StringUtils.hasText(request.getMerchantOrderNo())
                || StringUtils.hasText(request.getTransactionId())
                || StringUtils.hasText(request.getChannelOrderNo())
                || StringUtils.hasText(request.getChannelTransactionId())
                || StringUtils.hasText(request.getSourceTransactionId());
        if (!query && (!hasOrderIdentity || request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0 || !StringUtils.hasText(request.getCurrency()))) {
            throw new ChannelRequestException("ScottChannelA order identity, amount and currency are required");
        }
        if (refund && !StringUtils.hasText(firstText(request.getExtension().get("merchantRefundNo"),
                request.getMerchantOrderId()))) {
            throw new ChannelRequestException("ScottChannelA merchantRefundNo is required for refunds");
        }
    }

    private ChannelTradeStatus mapStatus(String status) {
        String normalized = normalize(status);
        return switch (normalized) {
            case "SUCCESS", "SUCCEEDED" -> ChannelTradeStatus.SUCCESS;
            case "FAIL", "FAILED", "CANCELLED", "CANCELED",
                    "ORDER_NOT_FOUND", "TRANSACTION_NOT_FOUND", "REFUND_NOT_FOUND" -> ChannelTradeStatus.FAILED;
            case "PROCESSING", "PENDING", "T201", "T202", "T203" -> ChannelTradeStatus.PROCESSING;
            default -> ChannelTradeStatus.PENDING;
        };
    }

    /**
     * 按 ScottChannelA 协议固定为两位小数发送金额。
     *
     * <p>交易库金额字段保留六位小数，直接序列化会产生 {@code 8.850000}，而渠道接口只接受最多两位小数。
     * 使用 {@link RoundingMode#UNNECESSARY} 禁止静默舍入，超过渠道精度的金额在发送前确定性失败。</p>
     */
    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ChannelRequestException("ScottChannelA amount supports at most 2 decimal places", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String body) {
        if (!StringUtils.hasText(body)) {
            throw new ChannelResponseException("ScottChannelA response body is empty");
        }
        Map<String, Object> value = JsonUtils.parseObject(body, Map.class);
        if (value == null) {
            throw new ChannelResponseException("ScottChannelA response body is invalid");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String requiredValue(ChannelPaymentRequest request, String key, String fallback) {
        return requiredValue(request, new String[]{key}, fallback);
    }

    private String requiredValue(ChannelPaymentRequest request, String key1, String key2, String key3,
                                 String key4, String key5, String fallback) {
        return requiredValue(request, new String[]{key1, key2, key3, key4, key5}, fallback);
    }

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

    private String text(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : String.valueOf(value);
    }

    private BigDecimal decimal(Map<String, Object> values, String key, BigDecimal fallback) {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private String resolveChannelPaymentMethod(String paymentMethod) {
        String normalized = normalize(paymentMethod);
        return switch (normalized) {
            case "BANK_CARD" -> "CARD";
            case "PAYPAL" -> "PAY_PAL";
            case "CASH_APP_PAY", "CASH_APP", "CASHAPP" -> "CASH_APP";
            default -> normalized;
        };
    }

    private void putIfText(Map<String, Object> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value);
        }
    }

    private void putRawResponseIfText(Map<String, String> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value);
        }
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
