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

    /**
     * callback Registry 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
