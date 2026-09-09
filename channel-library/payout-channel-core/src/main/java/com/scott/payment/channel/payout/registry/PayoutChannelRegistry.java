package com.scott.payment.channel.payout.registry;

import com.scott.payment.channel.payout.api.PayoutChannelClient;
import com.scott.payment.channel.payout.exception.PayoutChannelException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelRegistry
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 代付 Provider 注册器，按规范化 channel_code 定位实现并阻止重复注册。
 * @status : create
 */
@Component
public class PayoutChannelRegistry {

    private final Map<String, PayoutChannelClient> clients;

    /** 使用 Spring 容器中发现的独立代付 Provider 构建不可变注册表。 */
    public PayoutChannelRegistry(Optional<List<PayoutChannelClient>> channelClients) {
        Map<String, PayoutChannelClient> registry = new LinkedHashMap<>();
        for (PayoutChannelClient client : channelClients.orElseGet(Collections::emptyList)) {
            String channelCode = normalize(client.channelCode());
            if (channelCode == null || channelCode.isBlank()) {
                throw new PayoutChannelException("代付渠道编码不能为空");
            }
            if (registry.putIfAbsent(channelCode, client) != null) {
                throw new PayoutChannelException("代付渠道编码重复：" + channelCode);
            }
        }
        this.clients = Collections.unmodifiableMap(registry);
    }

    /** 按渠道编码返回必须存在的 Provider 实现。 */
    public PayoutChannelClient getRequired(String channelCode) {
        String normalized = normalize(channelCode);
        PayoutChannelClient client = clients.get(normalized);
        if (client == null) {
            throw new PayoutChannelException("未找到代付渠道客户端：" + normalized);
        }
        return client;
    }

    /** @return 已注册 Provider 的只读快照 */
    public Map<String, PayoutChannelClient> registeredClients() {
        return clients;
    }

    private String normalize(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }
}
