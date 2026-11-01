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
 * 代付 Provider 注册器，按规范化 channel_code 定位实现并阻止重复注册。
 */
@Component
public class PayoutChannelRegistry {

    private final Map<String, PayoutChannelClient> clients;

    /**
     * 使用 Spring 容器中发现的独立代付 Provider 构建不可变注册表。
     */
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

    /**
     * 按渠道编码返回必须存在的 Provider 实现。
     */
    public PayoutChannelClient getRequired(String channelCode) {
        String normalized = normalize(channelCode);
        PayoutChannelClient client = clients.get(normalized);
        if (client == null) {
            throw new PayoutChannelException("未找到代付渠道客户端：" + normalized);
        }
        return client;
    }

    /**
     * 返回已注册 Provider 的只读快照。
     *
     * @return 渠道编码到 Provider 的不可变映射
     */
    public Map<String, PayoutChannelClient> registeredClients() {
        return clients;
    }

    /**
     * 将渠道编码规范化为注册表使用的大写形式。
     *
     * @param channelCode 原始渠道编码
     * @return 规范化渠道编码
     */
    private String normalize(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }
}
