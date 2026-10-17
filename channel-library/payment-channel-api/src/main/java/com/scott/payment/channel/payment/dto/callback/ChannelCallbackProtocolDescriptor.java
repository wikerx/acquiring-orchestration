package com.scott.payment.channel.payment.dto.callback;

import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackProtocolDescriptor
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 不可变渠道回调协议描述，位于 payment-channel-api DTO 层，在进入渠道代码前约束 HTTP 方法、Content-Type、场景、正文大小和认证姿态。
 * @status : create
 */
public record ChannelCallbackProtocolDescriptor(Set<String> methods,
                                                Set<String> contentTypes,
                                                Set<ChannelCallbackScene> scenes,
                                                int maxBodyBytes,
                                                ChannelCallbackAuthentication authentication) {

    public ChannelCallbackProtocolDescriptor {
        methods = normalize(methods);
        contentTypes = normalize(contentTypes);
        scenes = scenes == null ? Set.of() : Set.copyOf(scenes);
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("callback methods can not be empty");
        }
        if (scenes.isEmpty()) {
            throw new IllegalArgumentException("callback scenes can not be empty");
        }
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("callback maxBodyBytes must be positive");
        }
        if (authentication == null) {
            throw new IllegalArgumentException("callback authentication can not be null");
        }
    }

    /** 判断请求方法是否属于渠道显式允许集合。 */
    public boolean supportsMethod(String method) {
        return method != null && methods.contains(method.trim().toLowerCase(Locale.ROOT));
    }

    /** 判断公网回调入口场景是否由该渠道协议支持。 */
    public boolean supportsScene(ChannelCallbackScene scene) {
        return scene != null && scenes.contains(scene);
    }

    /** 忽略 Content-Type 参数后校验媒体类型；空集合表示渠道不限制媒体类型。 */
    public boolean supportsContentType(String contentType) {
        if (contentTypes.isEmpty()) {
            return true;
        }
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return contentTypes.contains(mediaType);
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
