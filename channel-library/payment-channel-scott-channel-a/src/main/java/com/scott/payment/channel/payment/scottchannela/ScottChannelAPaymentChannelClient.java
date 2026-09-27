package com.scott.payment.channel.payment.scottchannela;

import com.scott.payment.channel.payment.api.AbstractPaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelReversalRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAPaymentChannelClient
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 收单 SPI 实现，只声明渠道文档提供的 direct payin、退款和查询能力，不伪造授权、捕获或 3DS 能力。
 * @status : create
 */
public class ScottChannelAPaymentChannelClient extends AbstractPaymentChannelClient {

    private final ScottChannelAApiClient apiClient;

    /**
     * 创建 ScottChannelA 收单 SPI 客户端。
     *
     * @param apiClient ScottChannelA HTTP 协议客户端
     */
    public ScottChannelAPaymentChannelClient(ScottChannelAApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** 返回管理系统使用的 ScottChannelA 渠道编码。 */
    @Override
    public String channelCode() {
        return ScottChannelACode.CODE;
    }

    /** 返回该 Provider 实际支持的收单能力。 */
    @Override
    public Set<ChannelCapability> capabilities() {
        return EnumSet.of(ChannelCapability.PAYMENT, ChannelCapability.REFUND, ChannelCapability.QUERY);
    }

    /** 发起直连代收请求。 */
    @Override
    public ChannelPaymentResponse payment(ChannelPaymentRequest request) {
        requireCapability(ChannelCapability.PAYMENT);
        return apiClient.execute(request);
    }

    /** 发起代收退款请求。 */
    @Override
    public ChannelPaymentResponse refund(ChannelRefundRequest request) {
        requireCapability(ChannelCapability.REFUND);
        return apiClient.execute(request);
    }

    /** 查询代收或退款状态。 */
    @Override
    public ChannelPaymentResponse query(ChannelQueryRequest request) {
        requireCapability(ChannelCapability.QUERY);
        return apiClient.execute(request);
    }

    @Override
    public ChannelPaymentResponse authorize(ChannelAuthorizeRequest request) {
        throw unsupported(ChannelCapability.AUTHORIZATION);
    }

    @Override
    public ChannelPaymentResponse capture(ChannelCaptureRequest request) {
        throw unsupported(ChannelCapability.CAPTURE);
    }

    @Override
    public ChannelPaymentResponse incrementalAuthorize(ChannelIncrementalAuthorizeRequest request) {
        throw unsupported(ChannelCapability.INCREMENTAL_AUTHORIZATION);
    }

    @Override
    public ChannelPaymentResponse preAuthorize(ChannelPreAuthorizeRequest request) {
        throw unsupported(ChannelCapability.PRE_AUTHORIZATION);
    }

    @Override
    public ChannelPaymentResponse voidPayment(ChannelVoidRequest request) {
        throw unsupported(ChannelCapability.VOID);
    }

    @Override
    public ChannelPaymentResponse reversal(ChannelReversalRequest request) {
        throw unsupported(ChannelCapability.REVERSAL);
    }
}
