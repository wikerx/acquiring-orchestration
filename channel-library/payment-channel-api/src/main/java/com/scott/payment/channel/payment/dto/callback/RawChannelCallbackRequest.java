package com.scott.payment.channel.payment.dto.callback;

import com.scott.payment.channel.payment.enums.ChannelCallbackScene;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RawChannelCallbackRequest
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 无损渠道回调输入，位于 payment-channel-api DTO 层，保留原始字节、多值 Header、路径和查询串供验签；构造和读取正文均执行防御性复制。
 * @status : create
 */
public record RawChannelCallbackRequest(String channelCode,
                                        ChannelCallbackScene scene,
                                        String method,
                                        String rawPath,
                                        String rawQuery,
                                        Map<String, List<String>> headers,
                                        byte[] body,
                                        String sourceIp) {

    public RawChannelCallbackRequest {
        channelCode = normalizeChannelCode(channelCode);
        method = method == null ? null : method.trim().toUpperCase(Locale.ROOT);
        headers = immutableHeaders(headers);
        body = body == null ? new byte[0] : body.clone();
    }

    /** 返回正文副本，防止渠道实现修改后影响后续验签或审计。 */
    @Override
    public byte[] body() {
        return body.clone();
    }

    /** 返回原始正文精确字节数。 */
    public int bodyLength() {
        return body.length;
    }

    /** 仅供明确声明 UTF-8 的渠道解析器读取文本正文。 */
    public String bodyUtf8() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /** 按 HTTP 大小写不敏感语义返回指定 Header 的全部值。 */
    public List<String> headerValues(String name) {
        if (name == null) {
            return List.of();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    /** 返回指定 Header 的首值，缺失时返回 null。 */
    public String firstHeader(String name) {
        List<String> values = headerValues(name);
        return values.isEmpty() ? null : values.get(0);
    }

    /** 生成兼容旧渠道解析器的首值 Header 只读视图。 */
    public Map<String, String> firstValueHeaders() {
        Map<String, String> firstValues = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                firstValues.put(name, values.get(0));
            }
        });
        return Collections.unmodifiableMap(firstValues);
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> {
            if (name != null) {
                copy.put(name, values == null
                        ? List.of()
                        : Collections.unmodifiableList(new ArrayList<>(values)));
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String normalizeChannelCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
