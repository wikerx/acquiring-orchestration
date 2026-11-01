package com.scott.payment.channel.payment.dto.callback;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次渠道回调验签上下文，位于 payment-channel-api DTO 层，只在内存中传递已定位的密钥材料、时钟偏差和当前时间，禁止持久化或日志输出。
 */
public record ChannelCallbackVerificationContext(String defaultSecret,
                                                 Map<String, String> eventSecrets,
                                                 long allowedClockSkewMillis,
                                                 long currentTimeMillis) {

    /**
     * 防御性复制按事件选择的验签密钥映射，避免回调处理期间被外部修改。
     */
    public ChannelCallbackVerificationContext {
        eventSecrets = eventSecrets == null || eventSecrets.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(eventSecrets));
    }
}
