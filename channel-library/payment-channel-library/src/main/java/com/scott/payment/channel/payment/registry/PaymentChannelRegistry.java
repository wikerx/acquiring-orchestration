package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelClient;
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
 * @classname : PaymentChannelRegistry
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道注册器，位于 payment-channel-library 注册层，用于按 channel_code 管理渠道客户端并阻止重复渠道编码。
 * @status : create
 */
@Component
public class PaymentChannelRegistry {

    private final Map<String, PaymentChannelClient> clients;

    /**
     * 创建渠道注册器。
     *
     * @param channelClients Spring 容器中的渠道客户端列表
     */
    public PaymentChannelRegistry(Optional<List<PaymentChannelClient>> channelClients) {
        Map<String, PaymentChannelClient> registry = new LinkedHashMap<>();
        for (PaymentChannelClient client : channelClients.orElseGet(Collections::emptyList)) {
            String channelCode = normalize(client.channelCode());
            if (!StringUtils.hasText(channelCode)) {
                throw new ChannelException("渠道编码不能为空");
            }
            if (registry.containsKey(channelCode)) {
                throw new ChannelException("渠道编码重复：" + channelCode);
            }
            registry.put(channelCode, client);
        }
        this.clients = Collections.unmodifiableMap(registry);
    }

    /**
     * 根据渠道编码获取渠道客户端。
     *
     * @param channelCode 渠道编码
     * @return 渠道客户端
     */
    public PaymentChannelClient getRequired(String channelCode) {
        String normalized = normalize(channelCode);
        PaymentChannelClient client = clients.get(normalized);
        if (client == null) {
            throw new ChannelException("未找到收单渠道客户端：" + normalized);
        }
        return client;
    }

    /**
     * 返回已注册渠道快照。
     *
     * @return 渠道客户端 Map
     */
    public Map<String, PaymentChannelClient> registeredClients() {
        return clients;
    }

    private String normalize(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }
}
