package com.scott.payment.channel.payout.scottchannela;

import com.scott.payment.channel.payout.api.PayoutChannelClient;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutCancelRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.enums.PayoutChannelCapability;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAPayoutClient
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 代付 Provider，声明 submit/query/cancel 能力并把协议调用委托给独立 HTTP 客户端。
 * @status : create
 */
public class ScottChannelAPayoutClient implements PayoutChannelClient {

    private final ScottChannelAPayoutApiClient apiClient;

    /**
     * 创建 ScottChannelA 代付 SPI 客户端。
     *
     * @param apiClient ScottChannelA 代付 HTTP 协议客户端
     */
    public ScottChannelAPayoutClient(ScottChannelAPayoutApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 返回管理系统使用的 ScottChannelA 渠道编码。 */
    @Override
    public String channelCode() {
        return ScottChannelAPayoutCode.CODE;
    }

    /** 返回该 Provider 实际支持的代付能力。 */
    @Override
    public Set<PayoutChannelCapability> capabilities() {
        return EnumSet.of(PayoutChannelCapability.SUBMIT, PayoutChannelCapability.QUERY, PayoutChannelCapability.CANCEL);
    }

    /** 提交代付请求。 */
    @Override
    public ChannelPayoutResponse submit(ChannelPayoutRequest request) {
        return apiClient.submit(request);
    }

    /** 查询代付状态。 */
    @Override
    public ChannelPayoutResponse query(ChannelPayoutQueryRequest request) {
        return apiClient.query(request);
    }

    /** 取消仍处于渠道处理中的代付。 */
    @Override
    public ChannelPayoutResponse cancel(ChannelPayoutCancelRequest request) {
        return apiClient.cancel(request);
    }
}
