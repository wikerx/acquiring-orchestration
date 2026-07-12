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
 * @description : 收单渠道回调执行器，位于 payment-channel-library 执行层，按 channelCode 分发到具体渠道回调处理器。
 * @status : create
 */
@Component
public class PaymentChannelCallbackExecutor {

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
