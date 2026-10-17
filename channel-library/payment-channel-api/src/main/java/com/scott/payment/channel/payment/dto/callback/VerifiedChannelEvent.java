package com.scott.payment.channel.payment.dto.callback;

import com.scott.payment.channel.payment.enums.ChannelCallbackKind;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : VerifiedChannelEvent
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 已认证渠道标准事件，位于 payment-channel-api DTO 层，只能由协议验签和解析成功后产生；支付核心不再接收原始报文进行二次猜测。
 * @status : create
 */
public record VerifiedChannelEvent(String channelCode,
                                   String credentialHint,
                                   String callbackEventId,
                                   ChannelCallbackKind callbackKind,
                                   String channelOrderNo,
                                   String channelTransactionId,
                                   String rawChannelStatus,
                                   String channelTradeStatus,
                                   BigDecimal amount,
                                   String currency,
                                   String channelResponseCode,
                                   String channelResponseMessage,
                                   Map<String, String> extension) {

    public VerifiedChannelEvent {
        extension = extension == null || extension.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extension));
    }

    /** 将既有渠道解析结果转换为不可变已认证事件。 */
    public static VerifiedChannelEvent from(ChannelCallbackResult result, String credentialHint) {
        if (result == null) {
            throw new IllegalArgumentException("verified callback result can not be null");
        }
        return new VerifiedChannelEvent(
                result.getChannelCode(),
                credentialHint,
                result.getCallbackEventId(),
                result.getCallbackKind(),
                result.getChannelOrderNo(),
                result.getChannelTransactionId(),
                result.getRawChannelStatus(),
                result.getChannelTradeStatus(),
                result.getAmount(),
                result.getCurrency(),
                result.getChannelResponseCode(),
                result.getChannelResponseMessage(),
                result.getExtension());
    }
}
