package com.scott.payment.channel.payment.worldpay.json.adapter;

import com.scott.payment.channel.payment.worldpay.adapter.AbstractPaymentChannelAdapter;
import com.scott.payment.channel.payment.worldpay.json.client.ApiClient;
import com.scott.payment.channel.payment.worldpay.support.ChannelCode;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;

/**
 * WorldPay JSON 收单渠道客户端，位于 payment-channel-worldpay 渠道实现层，负责把平台统一渠道能力委托给 WPGJSON HTTP 客户端；不创建平台交易单、不更新平台交易状态。
 */
public class PaymentChannelAdapter extends AbstractPaymentChannelAdapter {

    /**
     * WorldPay JSON API 客户端，负责渠道认证、HTTP 调用、脱敏日志和响应映射。
     */
    private final ApiClient apiClient;

    /**
     * 创建 WorldPay JSON 渠道客户端。
     *
     * @param apiClient WorldPay JSON API 客户端
     */
    public PaymentChannelAdapter(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取 WorldPay JSON 渠道编码。
     *
     * @return 渠道编码
     */
    @Override
    public String channelCode() {
        return ChannelCode.WPGJSON;
    }

    /**
     * 执行 WorldPay JSON 渠道请求。
     * <p>
     * 当前方法只进入 WPGJSON 协议适配层，真实平台状态推进由 service-payment 根据渠道统一响应和状态机完成。
     *
     * @param request 渠道统一请求
     * @return 渠道统一响应
     */
    @Override
    protected ChannelPaymentResponse execute(ChannelPaymentRequest request) {
        return apiClient.execute(request);
    }
}
