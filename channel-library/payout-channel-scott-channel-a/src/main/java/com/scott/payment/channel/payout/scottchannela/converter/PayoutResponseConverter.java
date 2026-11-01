package com.scott.payment.channel.payout.scottchannela.converter;

import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.enums.PayoutChannelStatus;
import com.scott.payment.channel.payout.exception.PayoutChannelException;
import com.scott.payment.channel.payout.scottchannela.model.response.ApiResponsePayload;
import com.scott.payment.channel.payout.scottchannela.model.response.PayoutResponsePayload;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 解释 ScottChannelA 代付同步响应，并保留原始状态、响应码和资金结果确定性。
 **/
public class PayoutResponseConverter {

    private static final Set<Integer> DETERMINISTIC_HTTP_FAILURES = Set.of(400, 401, 404);

    /**
     * 将渠道响应转换为平台统一代付响应。
     *
     * <p>限流、服务端错误、空响应和畸形成功响应都不能证明代付失败，必须保留为 unknown。</p>
     *
     * @param channelCode 平台稳定渠道编码
     * @param operationId 平台代付生命周期标识
     * @param payoutOrderNo 平台代付订单号
     * @param httpStatus 渠道 HTTP 状态码
     * @param responseBody 渠道原始响应正文
     * @return 包含原始证据的标准代付响应
     **/
    public ChannelPayoutResponse convert(String channelCode,
                                         String operationId,
                                         String payoutOrderNo,
                                         int httpStatus,
                                         String responseBody) {
        boolean successfulHttpStatus = httpStatus >= 200 && httpStatus < 300;
        boolean deterministicRejection = DETERMINISTIC_HTTP_FAILURES.contains(httpStatus);
        if (!successfulHttpStatus && !deterministicRejection) {
            throw new PayoutChannelException(
                    "ScottChannelA payout returned HTTP status " + httpStatus, null, true);
        }
        ApiResponsePayload root = parseResponse(responseBody, successfulHttpStatus);
        PayoutResponsePayload data = root.getData();
        if (successfulHttpStatus) {
            validateSuccessfulResponse(data);
        }
        String responseCode = firstText(data == null ? null : data.getResponseCode(), root.getCode(),
                deterministicRejection ? "HTTP_" + httpStatus : null);
        String status = deterministicRejection
                ? firstText(responseCode, "FAILED")
                : firstText(data == null ? null : data.getStatus(), root.getCode());
        ChannelPayoutResponse result = createResponse(
                channelCode, operationId, payoutOrderNo, httpStatus, root, data,
                responseCode, status, deterministicRejection);
        addRawEvidence(result, status);
        return result;
    }

    /**
     * 创建平台代付响应并恢复原操作身份。
     **/
    private ChannelPayoutResponse createResponse(String channelCode,
                                                 String operationId,
                                                 String payoutOrderNo,
                                                 int httpStatus,
                                                 ApiResponsePayload root,
                                                 PayoutResponsePayload data,
                                                 String responseCode,
                                                 String status,
                                                 boolean deterministicRejection) {
        ChannelPayoutResponse result = new ChannelPayoutResponse();
        result.setChannelCode(channelCode);
        result.setOperationId(operationId);
        result.setPayoutOrderNo(payoutOrderNo);
        result.setChannelOrderNo(data == null ? null : data.getMerchantOrderNo());
        result.setChannelTransactionId(data == null ? null : data.getTransactionId());
        result.setRawChannelStatus(status);
        result.setChannelPayoutStatus(deterministicRejection
                ? PayoutChannelStatus.FAILED.getCode() : mapStatus(status).getCode());
        result.setChannelResponseCode(responseCode);
        result.setChannelResponseMessage(firstText(
                data == null ? null : data.getResponseDescription(), root.getMessage()));
        result.setChannelCurrency(data == null ? null : data.getCurrency());
        result.setChannelAmount(data == null ? null : data.getAmount());
        result.setHttpStatus(httpStatus);
        return result;
    }

    /**
     * 保存后续勾兑和审计需要的渠道原始字段。
     **/
    private void addRawEvidence(ChannelPayoutResponse result, String status) {
        putRaw(result, "status", status);
        putRaw(result, "responseCode", result.getChannelResponseCode());
        putRaw(result, "responseDescription", result.getChannelResponseMessage());
    }

    /**
     * 解析渠道响应外层对象；Fastjson2 默认忽略未声明字段。
     **/
    private ApiResponsePayload parseResponse(String body, boolean outcomeUncertainOnInvalid) {
        if (!StringUtils.hasText(body)) {
            if (outcomeUncertainOnInvalid) {
                throw new PayoutChannelException("ScottChannelA payout response is empty", null, true);
            }
            return new ApiResponsePayload();
        }
        try {
            ApiResponsePayload response = JsonUtils.parseObject(body, ApiResponsePayload.class);
            if (response != null) {
                return response;
            }
        } catch (RuntimeException exception) {
            if (outcomeUncertainOnInvalid) {
                throw new PayoutChannelException("ScottChannelA payout response is invalid", exception, true);
            }
        }
        if (outcomeUncertainOnInvalid) {
            throw new PayoutChannelException("ScottChannelA payout response is invalid", null, true);
        }
        return new ApiResponsePayload();
    }

    /**
     * 校验成功响应的最低交易证据，缺失时保留为 unknown。
     **/
    private void validateSuccessfulResponse(PayoutResponsePayload data) {
        if (data == null || !StringUtils.hasText(data.getStatus())
                || !StringUtils.hasText(data.getMerchantOrderNo())
                || !StringUtils.hasText(data.getTransactionId())) {
            throw new PayoutChannelException("ScottChannelA payout response is incomplete", null, true);
        }
    }

    /**
     * 将渠道代付状态映射为平台状态；未知值保守保持待处理。
     **/
    private PayoutChannelStatus mapStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return PayoutChannelStatus.PENDING;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED" -> PayoutChannelStatus.SUCCESS;
            case "FAIL", "FAILED" -> PayoutChannelStatus.FAILED;
            case "CANCELLED", "CANCELED" -> PayoutChannelStatus.RETURNED;
            case "PROCESSING", "PENDING" -> PayoutChannelStatus.PROCESSING;
            default -> PayoutChannelStatus.PENDING;
        };
    }

    /**
     * 仅将非空渠道原始证据写入统一代付响应。
     *
     * @param result 统一代付响应
     * @param key    证据字段名
     * @param value  证据字段值
     **/
    private void putRaw(ChannelPayoutResponse result, String key, String value) {
        if (StringUtils.hasText(value)) {
            result.getRawResponse().put(key, value);
        }
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
}
