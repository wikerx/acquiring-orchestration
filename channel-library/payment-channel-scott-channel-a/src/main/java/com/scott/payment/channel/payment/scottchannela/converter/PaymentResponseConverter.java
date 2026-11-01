package com.scott.payment.channel.payment.scottchannela.converter;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.scottchannela.model.response.ApiResponsePayload;
import com.scott.payment.channel.payment.scottchannela.model.response.TransactionResponsePayload;
import com.scott.payment.channel.payment.scottchannela.support.ChannelCode;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.isCardAuthorizationRequest;
import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.isRefundQuery;
import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.effectiveOperation;
import static com.scott.payment.channel.payment.scottchannela.converter.PaymentOperationClassifier.normalize;

/**
 * 解释 ScottChannelA 同步响应，并保留渠道原始状态、响应码和资金结果确定性。
 **/
public class PaymentResponseConverter {

    private static final Set<Integer> DETERMINISTIC_HTTP_FAILURES = Set.of(400, 401, 404);

    /**
     * 将渠道响应转换为平台统一响应。
     *
     * <p>限流、服务端错误、空响应和畸形成功响应均不能证明资金动作失败，必须以 unknown 异常返回。</p>
     *
     * @param request 原平台请求，用于恢复幂等身份和识别操作类型
     * @param httpStatus 渠道 HTTP 状态码
     * @param responseBody 渠道原始响应正文
     * @return 包含原始证据的标准渠道响应
     * @throws ChannelResponseException 渠道结果无法安全解释时抛出
     **/
    public ChannelPaymentResponse convert(ChannelPaymentRequest request, int httpStatus, String responseBody) {
        boolean successfulHttpStatus = httpStatus >= 200 && httpStatus < 300;
        boolean deterministicRejection = DETERMINISTIC_HTTP_FAILURES.contains(httpStatus);
        if (!successfulHttpStatus && !deterministicRejection) {
            throw new ChannelResponseException(
                    "ScottChannelA returned HTTP status " + httpStatus, null, true);
        }
        ApiResponsePayload root = parseResponse(responseBody, successfulHttpStatus);
        TransactionResponsePayload data = root.getData();
        if (successfulHttpStatus && data == null) {
            throw new ChannelResponseException("ScottChannelA successful response data is invalid");
        }
        if (successfulHttpStatus) {
            validateSuccessfulResponse(request, data, root.getCode());
        }
        String responseStatus = data == null ? null : firstText(data.getStatus(), processingCode(root.getCode()));
        String responseCode = firstText(data == null ? null : data.getResponseCode(), root.getCode(),
                deterministicRejection ? "HTTP_" + httpStatus : null);
        String status = deterministicRejection
                ? firstText(responseCode, "FAILED")
                : firstText(responseStatus, root.getCode(), "PROCESSING");
        ChannelPaymentResponse result = createResponse(request, data, httpStatus, responseCode,
                root.getMessage(), status, deterministicRejection);
        addRawEvidence(result, data, status);
        return result;
    }

    /**
     * 创建平台响应并恢复原操作的业务身份。
     **/
    private ChannelPaymentResponse createResponse(ChannelPaymentRequest request,
                                                  TransactionResponsePayload data,
                                                  int httpStatus,
                                                  String responseCode,
                                                  String rootMessage,
                                                  String status,
                                                  boolean deterministicRejection) {
        ChannelPaymentResponse result = new ChannelPaymentResponse();
        result.setChannelCode(ChannelCode.VALUE);
        result.setOperationId(request.getOperationId());
        result.setTransactionId(request.getTransactionId());
        result.setChannelOrderNo(isCardAuthorizationRequest(request)
                ? firstText(data == null ? null : data.getAuthorizationId(), request.getChannelOrderNo())
                : firstText(data == null ? null : data.getMerchantOrderNo(), request.getChannelOrderNo()));
        boolean refundResponse = ChannelCapability.REFUND.getCode().equals(normalize(request.getTransactionType()))
                || (ChannelCapability.QUERY.getCode().equals(normalize(request.getTransactionType()))
                && isRefundQuery(request));
        result.setChannelTransactionId(refundResponse
                ? firstText(data == null ? null : data.getRefundId(), request.getChannelTransactionId())
                : firstText(data == null ? null : data.getTransactionId(), request.getChannelTransactionId()));
        result.setRawChannelStatus(status);
        result.setChannelTradeStatus(deterministicRejection
                ? ChannelTradeStatus.FAILED.getCode()
                : mapStatus(request, status).getCode());
        result.setChannelResponseCode(responseCode);
        result.setChannelResponseMessage(firstText(
                data == null ? null : data.getResponseDescription(),
                rootMessage));
        result.setAuthCode(data == null ? null : data.getAuthorizationCode());
        result.setAcquirerReferenceNo(data == null ? null : data.getAcquirerReferenceNumber());
        result.setChannelCurrency(data == null ? null : data.getCurrency());
        result.setChannelAmount(data == null ? null : data.getAmount());
        result.setRedirectUrl(data == null ? null : data.getCheckoutUrl());
        result.setHttpStatus(httpStatus);
        return result;
    }

    /**
     * 保存后续勾兑和审计需要的渠道原始字段。
     **/
    private void addRawEvidence(ChannelPaymentResponse result, TransactionResponsePayload data, String status) {
        result.getRawResponse().put("status", status);
        result.getRawResponse().put("responseCode", result.getChannelResponseCode());
        result.getRawResponse().put("responseDescription", result.getChannelResponseMessage());
        putRawIfText(result.getRawResponse(), "merchantRefundNo", data == null ? null : data.getMerchantRefundNo());
        putRawIfText(result.getRawResponse(), "refundId", data == null ? null : data.getRefundId());
        putRawIfText(result.getRawResponse(), "merchantOrderNo", data == null ? null : data.getMerchantOrderNo());
        putRawIfText(result.getRawResponse(), "authorizationId", data == null ? null : data.getAuthorizationId());
        putRawIfText(result.getRawResponse(), "operationType", data == null ? null : data.getOperationType());
        putRawIfText(result.getRawResponse(), "authorizationStatus",
                data == null ? null : data.getAuthorizationStatus());
        putRawIfText(result.getRawResponse(), "authorizationCode", result.getAuthCode());
        putRawIfText(result.getRawResponse(), "acquirerReferenceNumber", result.getAcquirerReferenceNo());
    }

    /**
     * 解析渠道响应外层对象；Fastjson2 默认忽略未声明字段。
     **/
    private ApiResponsePayload parseResponse(String body, boolean outcomeUncertainOnInvalid) {
        if (!StringUtils.hasText(body)) {
            if (outcomeUncertainOnInvalid) {
                throw new ChannelResponseException("ScottChannelA response body is empty");
            }
            return new ApiResponsePayload();
        }
        try {
            ApiResponsePayload value = JsonUtils.parseObject(body, ApiResponsePayload.class);
            if (value != null) {
                return value;
            }
        } catch (RuntimeException exception) {
            if (outcomeUncertainOnInvalid) {
                throw new ChannelResponseException("ScottChannelA response body is invalid", exception);
            }
        }
        if (outcomeUncertainOnInvalid) {
            throw new ChannelResponseException("ScottChannelA response body is invalid");
        }
        return new ApiResponsePayload();
    }

    /**
     * 校验成功响应的最低交易证据，缺失时保留为 unknown。
     **/
    private void validateSuccessfulResponse(ChannelPaymentRequest request,
                                            TransactionResponsePayload data,
                                            String rootCode) {
        if (!StringUtils.hasText(data.getStatus())
                && !StringUtils.hasText(processingCode(rootCode))) {
            throw new ChannelResponseException("ScottChannelA successful response status is missing");
        }
        if (isCardAuthorizationRequest(request)) {
            if (!StringUtils.hasText(data.getAuthorizationId())
                    || !StringUtils.hasText(data.getTransactionId())
                    || !StringUtils.hasText(data.getMerchantOrderNo())) {
                throw new ChannelResponseException("ScottChannelA authorization response is incomplete");
            }
            return;
        }
        if (ChannelCapability.REFUND.getCode().equals(normalize(request.getTransactionType()))
                || isRefundQuery(request)) {
            if (!StringUtils.hasText(data.getRefundId())
                    || !StringUtils.hasText(data.getMerchantRefundNo())) {
                throw new ChannelResponseException("ScottChannelA refund response is incomplete");
            }
            return;
        }
        if (!StringUtils.hasText(data.getMerchantOrderNo())
                || !StringUtils.hasText(data.getTransactionId())) {
            throw new ChannelResponseException("ScottChannelA payin response is incomplete");
        }
    }

    /**
     * 将渠道状态映射为平台状态；未知值保持待处理。
     **/
    private ChannelTradeStatus mapStatus(ChannelPaymentRequest request, String status) {
        return switch (normalize(status)) {
            case "SUCCESS", "SUCCEEDED" -> ChannelTradeStatus.SUCCESS;
            case "CANCELLED", "CANCELED" -> ChannelCapability.VOID.getCode().equals(effectiveOperation(request))
                    ? ChannelTradeStatus.VOIDED : ChannelTradeStatus.FAILED;
            case "FAIL", "FAILED",
                    "ORDER_NOT_FOUND", "TRANSACTION_NOT_FOUND", "REFUND_NOT_FOUND" -> ChannelTradeStatus.FAILED;
            case "PROCESSING", "PENDING", "T201", "T202", "T203" -> ChannelTradeStatus.PROCESSING;
            default -> ChannelTradeStatus.PENDING;
        };
    }

    /**
     * 识别渠道外层响应码中表示处理中结果的稳定编码。
     *
     * @param code 渠道响应码
     * @return 处理中编码；其他编码返回 {@code null}
     **/
    private String processingCode(String code) {
        String normalized = normalize(code);
        return Set.of("PROCESSING", "PENDING", "T201", "T202", "T203").contains(normalized)
                ? normalized : null;
    }

    /**
     * 按优先级返回第一个非空文本。
     *
     * @param values 候选值
     * @return 首个非空值或 {@code null}
     **/
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
     * 仅将非空渠道原始证据写入审计映射。
     *
     * @param values 原始证据映射
     * @param key    证据字段名
     * @param value  证据字段值
     **/
    private void putRawIfText(Map<String, String> values, String key, String value) {
        if (StringUtils.hasText(value)) {
            values.put(key, value);
        }
    }
}
