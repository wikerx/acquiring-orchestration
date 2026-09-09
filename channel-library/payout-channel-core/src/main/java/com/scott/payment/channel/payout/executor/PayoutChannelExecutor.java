package com.scott.payment.channel.payout.executor;

import com.scott.payment.channel.payout.api.PayoutChannelClient;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.enums.PayoutChannelCapability;
import com.scott.payment.channel.payout.registry.PayoutChannelRegistry;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelExecutor
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 代付渠道执行器，通过 Registry 委托独立 Provider，不包含 service-payout 状态机或路由规则。
 * @status : create
 */
@Component
public class PayoutChannelExecutor {

    private final PayoutChannelRegistry channelRegistry;

    public PayoutChannelExecutor(PayoutChannelRegistry channelRegistry) {
        this.channelRegistry = channelRegistry;
    }

    /** 按请求中的 channelCode 提交代付。 */
    public ChannelPayoutResponse submit(ChannelPayoutRequest request) {
        PayoutChannelClient client = channelRegistry.getRequired(request.getChannelCode());
        if (!client.supports(PayoutChannelCapability.SUBMIT)) {
            throw client.unsupported(PayoutChannelCapability.SUBMIT);
        }
        return client.submit(request);
    }

    /** 按请求中的 channelCode 查询代付。 */
    public ChannelPayoutResponse query(ChannelPayoutQueryRequest request) {
        PayoutChannelClient client = channelRegistry.getRequired(request.getChannelCode());
        if (!client.supports(PayoutChannelCapability.QUERY)) {
            throw client.unsupported(PayoutChannelCapability.QUERY);
        }
        return client.query(request);
    }

    /** 判断指定 Provider 是否声明某项代付能力。 */
    public boolean supports(String channelCode, PayoutChannelCapability capability) {
        return channelRegistry.getRequired(channelCode).supports(capability);
    }
}
