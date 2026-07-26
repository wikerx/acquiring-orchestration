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
     * clients 依赖，用于 Payment Channel Registry 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 渠道适配库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param channelCode channel Code 输入值，参与 渠道编码 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String normalize(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }
}
