package com.scott.payment.channel.payment.scottchannela;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelCallbackKind;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelACallbackHandler
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 回调解析器，统一处理代收、退款和代付事件；只产出渠道事件，不直接修改平台交易状态。
 * @status : create
 */
@SuppressWarnings("unchecked")
public class ScottChannelACallbackHandler implements PaymentChannelCallbackHandler {

    /** 返回回调注册器使用的 ScottChannelA 渠道编码。 */
    @Override
    public String channelCode() {
        return ScottChannelACode.CODE;
    }

    /** 解析并校验 Header 与回调正文中的事件标识一致性。 */
    @Override
    public ChannelCallbackResult handle(ChannelCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getBody())) {
            throw new ChannelRequestException("ScottChannelA callback body is required");
        }
        Map<String, Object> body = JsonUtils.parseObject(request.getBody(), Map.class);
        if (body == null) {
            throw new ChannelRequestException("ScottChannelA callback body is invalid");
        }
        ChannelCallbackResult result = new ChannelCallbackResult();
        result.setChannelCode(ScottChannelACode.CODE);
        result.setCallbackKind(ChannelCallbackKind.FINANCIAL_TRANSACTION);
        String headerMerchantId = header(request, ScottChannelACallbackVerifier.MERCHANT_ID_HEADER);
        String headerEventId = header(request, ScottChannelACallbackVerifier.EVENT_ID_HEADER);
        String bodyMerchantId = text(body, "merchantId");
        String bodyEventId = text(body, "eventId");
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
        result.setChannelOrderNo(text(body, "merchantOrderNo"));
        boolean refundCallback = "REFUND_CALLBACK".equalsIgnoreCase(text(body, "eventType"))
                || StringUtils.hasText(text(body, "refundId"));
        result.setChannelTransactionId(refundCallback
                ? firstText(text(body, "refundId"), text(body, "transactionId"))
                : text(body, "transactionId"));
        result.setRawChannelStatus(text(body, "status"));
        result.setChannelTradeStatus(mapStatus(result.getRawChannelStatus()).getCode());
        result.setAmount(decimal(body, "amount"));
        result.setCurrency(text(body, "currency"));
        result.setSignatureValid(true);
        result.setChannelResponseCode(firstText(text(body, "responseCode"), text(body, "code")));
        result.setChannelResponseMessage(firstText(text(body, "responseDescription"), text(body, "message")));
        put(result, "businessType", text(body, "businessType"));
        put(result, "paymentScene", text(body, "paymentScene"));
        put(result, "paymentMethod", text(body, "paymentMethod"));
        put(result, "merchantRefundNo", text(body, "merchantRefundNo"));
        put(result, "refundId", text(body, "refundId"));
        return result;
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

    private String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimal(Map<String, Object> body, String key) {
        String value = text(body, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new ChannelRequestException("ScottChannelA callback amount is invalid");
        }
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
