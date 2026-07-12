package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.exception.ChannelException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackRegistry
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道回调注册器，位于 payment-channel-library 注册层，用于按 channel_code 管理渠道回调处理器并阻止重复注册。
 * @status : create
 */
@Component
public class PaymentChannelCallbackRegistry {

    private final Map<String, PaymentChannelCallbackHandler> handlers;

    /**
     * 创建渠道回调注册器。
     *
     * @param callbackHandlers Spring 容器中的渠道回调处理器列表
     */
    public PaymentChannelCallbackRegistry(Optional<List<PaymentChannelCallbackHandler>> callbackHandlers) {
        Map<String, PaymentChannelCallbackHandler> registry = new LinkedHashMap<>();
        for (PaymentChannelCallbackHandler handler : callbackHandlers.orElseGet(Collections::emptyList)) {
            String channelCode = normalize(handler.channelCode());
            if (!StringUtils.hasText(channelCode)) {
                throw new ChannelException("渠道回调编码不能为空");
            }
            if (registry.containsKey(channelCode)) {
                throw new ChannelException("渠道回调编码重复：" + channelCode);
            }
            registry.put(channelCode, handler);
        }
        this.handlers = Collections.unmodifiableMap(registry);
    }

    /**
     * 根据渠道编码获取回调处理器。
     *
     * @param channelCode 渠道编码
     * @return 渠道回调处理器
     */
    public PaymentChannelCallbackHandler getRequired(String channelCode) {
        String normalized = normalize(channelCode);
        PaymentChannelCallbackHandler handler = handlers.get(normalized);
        if (handler == null) {
            throw new ChannelException("未找到收单渠道回调处理器：" + normalized);
        }
        return handler;
    }

    /**
     * 返回已注册回调处理器快照。
     *
     * @return 回调处理器 Map
     */
    public Map<String, PaymentChannelCallbackHandler> registeredHandlers() {
        return handlers;
    }

    private String normalize(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }
}
