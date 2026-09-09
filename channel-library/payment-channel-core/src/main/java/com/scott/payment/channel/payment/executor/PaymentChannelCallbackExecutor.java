package com.scott.payment.channel.payment.executor;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.registry.PaymentChannelCallbackRegistry;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackExecutor
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道回调执行器，位于 payment-channel-core 执行层，按 channelCode 分发到具体渠道回调处理器。
 * @status : create
 */
@Component
public class PaymentChannelCallbackExecutor {

    /**
     * 回调注册表字段，保存 支付渠道回调执行器 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
     * </p>
     */
    private final PaymentChannelCallbackRegistry callbackRegistry;

    /**
     * 创建渠道回调执行器。
     *
     * @param callbackRegistry 渠道回调注册器
     */
    public PaymentChannelCallbackExecutor(PaymentChannelCallbackRegistry callbackRegistry) {
        this.callbackRegistry = callbackRegistry;
    }

    /**
     * 执行渠道回调解析。
     *
     * @param request 渠道回调请求
     * @return 渠道回调解析结果
     */
    public ChannelCallbackResult execute(ChannelCallbackRequest request) {
        PaymentChannelCallbackHandler handler = callbackRegistry.getRequired(request.getChannelCode());
        return handler.handle(request);
    }
}
