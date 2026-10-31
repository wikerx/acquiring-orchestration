package com.scott.payment.channel.payout.scottchannela.adapter;

import com.scott.payment.channel.payout.api.PayoutChannelClient;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutCancelRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.enums.PayoutChannelCapability;
import com.scott.payment.channel.payout.scottchannela.client.PayoutApiClient;
import com.scott.payment.channel.payout.scottchannela.support.ChannelCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * 声明提交、查询和取消能力的 ScottChannelA 代付 SPI。
 **/
public class ScottChannelAPayoutAdapter implements PayoutChannelClient {

    private final PayoutApiClient apiClient;

    /**
     * 创建 ScottChannelA 代付 SPI 客户端。
     *
     * @param apiClient ScottChannelA 代付 HTTP 协议客户端
     **/
    public ScottChannelAPayoutAdapter(PayoutApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 返回管理系统和代付注册器使用的稳定渠道编码。
     *
     * @return {@code SCOTT_CHANNEL_A}
     **/
    @Override
    public String channelCode() {
        return ChannelCode.VALUE;
    }

    /**
     * 返回 ScottChannelA 已实现并通过适配器验证的代付能力。
     *
     * @return 提交、查询和取消能力集合
     **/
    @Override
    public Set<PayoutChannelCapability> capabilities() {
        return EnumSet.of(PayoutChannelCapability.SUBMIT, PayoutChannelCapability.QUERY, PayoutChannelCapability.CANCEL);
    }

    /**
     * 使用平台代付单号作为渠道幂等键提交代付。
     *
     * @param request 包含金额、币种、方式、收款引用和原交易 MID 元数据的请求
     * @return 标准化的渠道受理结果和审计证据
     **/
    @Override
    public ChannelPayoutResponse submit(ChannelPayoutRequest request) {
        return apiClient.submit(request);
    }

    /**
     * 使用原交易持久化的渠道身份和 MID 查询代付状态。
     *
     * @param request 代付查询请求
     * @return 标准化的渠道查询结果
     **/
    @Override
    public ChannelPayoutResponse query(ChannelPayoutQueryRequest request) {
        return apiClient.query(request);
    }

    /**
     * 使用原交易持久化的渠道身份和 MID 取消仍在处理中的代付。
     *
     * @param request 代付取消请求
     * @return 标准化的渠道取消结果
     **/
    @Override
    public ChannelPayoutResponse cancel(ChannelPayoutCancelRequest request) {
        return apiClient.cancel(request);
    }
}
