package com.scott.payment.channel.payment.dto.callback;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackVerificationRequest
 * @date : 2026-08-12 00:00
 * @description : 渠道回调验签输入，只承载协议中立 HTTP 元数据和由安全配置解析后的密钥，不包含 Servlet 或平台交易模型。
 * @status : create
 */
public record ChannelCallbackVerificationRequest(String channelCode,
                                                 String method,
                                                 String path,
                                                 Map<String, String> headers,
                                                 String rawBody,
                                                 String defaultSecret,
                                                 Map<String, String> eventSecrets,
                                                 long allowedClockSkewMillis,
                                                 long currentTimeMillis) {

    /** 创建不可变验签上下文，并防止调用方后续修改请求头或密钥映射。 */
    public ChannelCallbackVerificationRequest {
        headers = immutableCopy(headers);
        eventSecrets = immutableCopy(eventSecrets);
    }

    /** 按大小写不敏感规则读取 HTTP 请求头。 */
    public String header(String name) {
        if (name == null) {
            return null;
        }
        String direct = headers.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 返回规范化的大写渠道编码。 */
    public String normalizedChannelCode() {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, String> immutableCopy(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
