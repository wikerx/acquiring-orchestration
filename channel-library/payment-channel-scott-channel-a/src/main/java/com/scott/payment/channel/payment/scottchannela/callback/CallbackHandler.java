package com.scott.payment.channel.payment.scottchannela.callback;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelCallbackKind;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.scottchannela.model.callback.CallbackPayload;
import com.scott.payment.channel.payment.scottchannela.support.ChannelCode;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * ScottChannelA 回调解析器，校验事件身份并输出协议中立的渠道事件。
 *
 * <p>卡授权回调以 authorizationId 作为渠道订单身份，并保留授权码、ARN 和授权聚合证据；
 * 该类不直接推进平台交易状态。</p>
 **/
public class CallbackHandler {

    /**
     * 返回回调注册器使用的 ScottChannelA 渠道编码。
     *
     * @return 稳定渠道编码 {@code SCOTT_CHANNEL_A}
     **/
    public String channelCode() {
        return ChannelCode.VALUE;
    }

    /**
     * 解析已验签回调并校验请求头与正文的 MID、事件号及交易身份。
     *
     * <p>未知渠道状态保守映射为待处理，避免未来新增状态覆盖平台终态。授权码、ARN 和授权金额
     * 只作为证据写入扩展字段，实际交易状态推进由支付核心完成。</p>
     *
     * @param request 已由协议层转换的回调请求
     * @return 平台统一渠道回调结果
     * @throws ChannelRequestException 正文无效、必填字段缺失或请求头与正文身份不一致时抛出
     **/
    public ChannelCallbackResult handle(ChannelCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getBody())) {
            throw new ChannelRequestException("ScottChannelA callback body is required");
        }
        CallbackPayload body = parsePayload(request.getBody());
        ChannelCallbackResult result = new ChannelCallbackResult();
        result.setChannelCode(ChannelCode.VALUE);
        result.setCallbackKind(ChannelCallbackKind.FINANCIAL_TRANSACTION);
        String headerMerchantId = header(request, CallbackVerifier.MERCHANT_ID_HEADER);
        String headerEventId = header(request, CallbackVerifier.EVENT_ID_HEADER);
        String bodyMerchantId = body.getMerchantId();
        String bodyEventId = body.getEventId();
        if (!StringUtils.hasText(bodyMerchantId)) {
            throw new ChannelRequestException("ScottChannelA callback merchantId is required");
        }
        if (StringUtils.hasText(headerMerchantId)
                && !headerMerchantId.trim().equals(bodyMerchantId.trim())) {
            throw new ChannelRequestException("ScottChannelA callback merchant id does not match header and body");
        }
        if (!StringUtils.hasText(bodyEventId)) {
            throw new ChannelRequestException("ScottChannelA callback eventId is required");
        }
        if (StringUtils.hasText(headerEventId)
                && !headerEventId.trim().equals(bodyEventId.trim())) {
            throw new ChannelRequestException("ScottChannelA callback event id does not match header and body");
        }
        result.setCallbackEventId(firstText(headerEventId, bodyEventId));
        if (!StringUtils.hasText(body.getStatus())) {
            throw new ChannelRequestException("ScottChannelA callback status is required");
        }
        boolean authorizationCallback = "CARD_AUTH_CALLBACK".equalsIgnoreCase(body.getEventType());
        result.setChannelOrderNo(authorizationCallback
                ? body.getAuthorizationId()
                : body.getMerchantOrderNo());
        boolean refundCallback = "REFUND_CALLBACK".equalsIgnoreCase(body.getEventType())
                || StringUtils.hasText(body.getRefundId());
        result.setChannelTransactionId(refundCallback
                ? firstText(body.getRefundId(), body.getTransactionId())
                : body.getTransactionId());
        if (!StringUtils.hasText(result.getChannelOrderNo())
                || !StringUtils.hasText(result.getChannelTransactionId())) {
            throw new ChannelRequestException("ScottChannelA callback transaction identity is incomplete");
        }
        result.setRawChannelStatus(body.getStatus());
        result.setChannelTradeStatus(mapStatus(result.getRawChannelStatus()).getCode());
        result.setAmount(body.getAmount());
        result.setCurrency(body.getCurrency());
        result.setChannelResponseCode(firstText(body.getResponseCode(), body.getCode()));
        result.setChannelResponseMessage(firstText(body.getResponseDescription(), body.getMessage()));
        put(result, "businessType", body.getBusinessType());
        put(result, "paymentScene", body.getPaymentScene());
        put(result, "paymentMethod", body.getPaymentMethod());
        put(result, "merchantRefundNo", body.getMerchantRefundNo());
        put(result, "refundId", body.getRefundId());
        put(result, "merchantOrderNo", body.getMerchantOrderNo());
        put(result, "authorizationId", body.getAuthorizationId());
        put(result, "operationType", body.getOperationType());
        put(result, "authorizationStatus", body.getAuthorizationStatus());
        put(result, "authorizationCode", body.getAuthorizationCode());
        put(result, "acquirerReferenceNumber", body.getAcquirerReferenceNumber());
        put(result, "authorizedAmount", decimalText(body.getAuthorizedAmount()));
        put(result, "capturedAmount", decimalText(body.getCapturedAmount()));
        put(result, "availableAmount", decimalText(body.getAvailableAmount()));
        return result;
    }

    /**
     * 将 JSON 正文解析为强类型回调对象。
     *
     * <p>Fastjson2 默认忽略模型未声明的字段，以兼容渠道增加非关键参数。</p>
     **/
    private CallbackPayload parsePayload(String body) {
        try {
            CallbackPayload payload = JsonUtils.parseObject(body, CallbackPayload.class);
            if (payload != null) {
                return payload;
            }
        } catch (RuntimeException exception) {
            throw new ChannelRequestException("ScottChannelA callback body is invalid", exception);
        }
        throw new ChannelRequestException("ScottChannelA callback body is invalid");
    }

    private ChannelTradeStatus mapStatus(String status) {
        if (status == null) {
            return ChannelTradeStatus.PENDING;
        }
        return switch (status.trim().toUpperCase()) {
            case "SUCCESS", "SUCCEEDED" -> ChannelTradeStatus.SUCCESS;
            case "FAIL", "FAILED", "CANCELLED", "CANCELED" -> ChannelTradeStatus.FAILED;
            case "PROCESSING", "T201", "T202", "T203" -> ChannelTradeStatus.PROCESSING;
            default -> ChannelTradeStatus.PENDING;
        };
    }

    private String header(ChannelCallbackRequest request, String name) {
        if (request.getHeaders() == null) {
            return null;
        }
        return request.getHeaders().entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String decimalText(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private void put(ChannelCallbackResult result, String key, String value) {
        if (StringUtils.hasText(value)) {
            result.getExtension().put(key, value);
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
}
