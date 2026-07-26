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

    /**
     * clients 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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

    /**
     * 标准化 normalize 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param channelCode channel Code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private String normalize(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }
}
