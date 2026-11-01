package com.scott.payment.channel.payment.scottchannela.adapter;

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
import com.scott.payment.channel.payment.scottchannela.client.PaymentApiClient;
import com.scott.payment.channel.payment.scottchannela.support.ChannelCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * ScottChannelA 收单 SPI 实现，声明并委托渠道实际支持的代收、退款、卡授权生命周期和查询能力。
 *
 * <p>渠道未发布冲正接口，因此 REVERSAL 始终明确拒绝。</p>
 **/
public class PaymentChannelAdapter extends AbstractPaymentChannelClient {

    private final PaymentApiClient apiClient;

    /**
     * 创建 ScottChannelA 收单 SPI 客户端。
     *
     * @param apiClient ScottChannelA HTTP 协议客户端
     **/
    public PaymentChannelAdapter(PaymentApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 返回管理系统和渠道注册器使用的稳定渠道编码。
     *
     * @return {@code SCOTT_CHANNEL_A}
     **/
    @Override
    public String channelCode() {
        return ChannelCode.VALUE;
    }

    /**
     * 返回 ScottChannelA 已实现并通过适配器验证的收单能力。
     *
     * @return 不包含 REVERSAL 的能力集合
     **/
    @Override
    public Set<ChannelCapability> capabilities() {
        return EnumSet.of(
                ChannelCapability.PAYMENT,
                ChannelCapability.AUTHORIZATION,
                ChannelCapability.PRE_AUTHORIZATION,
                ChannelCapability.INCREMENTAL_AUTHORIZATION,
                ChannelCapability.CAPTURE,
                ChannelCapability.PRE_AUTH_COMPLETION,
                ChannelCapability.REFUND,
                ChannelCapability.VOID,
                ChannelCapability.QUERY);
    }

    /**
     * 发起直连代收请求。
     *
     * @param request 包含平台交易号、金额、币种、支付方式和原交易 MID 元数据的请求
     * @return 标准化的渠道受理结果和审计证据
     **/
    @Override
    public ChannelPaymentResponse payment(ChannelPaymentRequest request) {
        requireCapability(ChannelCapability.PAYMENT);
        return apiClient.execute(request);
    }

    /**
     * 对原代收交易发起退款。
     *
     * @param request 包含退款交易号及原支付渠道身份的请求
     * @return 标准化的渠道退款结果和审计证据
     **/
    @Override
    public ChannelPaymentResponse refund(ChannelRefundRequest request) {
        requireCapability(ChannelCapability.REFUND);
        return apiClient.execute(request);
    }

    /**
     * 使用原交易持久化的渠道身份查询代收、退款或卡授权状态。
     *
     * @param request 查询请求；退款和授权查询类型由原交易扩展证据确定
     * @return 标准化的渠道查询结果
     **/
    @Override
    public ChannelPaymentResponse query(ChannelQueryRequest request) {
        requireCapability(ChannelCapability.QUERY);
        return apiClient.execute(request);
    }

    /**
     * 发起普通卡授权。
     *
     * @param request 包含平台系统交易号、金额和币种的授权请求
     * @return 包含渠道授权号、授权码及当前状态的标准响应
     **/
    @Override
    public ChannelPaymentResponse authorize(ChannelAuthorizeRequest request) {
        requireCapability(ChannelCapability.AUTHORIZATION);
        return apiClient.execute(request);
    }

    /**
     * 按交易类型发起普通请款或预授权完成。
     *
     * @param request 包含原渠道授权号和本次操作交易号的请款请求
     * @return 包含授权码、ARN 及当前状态的标准响应
     **/
    @Override
    public ChannelPaymentResponse capture(ChannelCaptureRequest request) {
        ChannelCapability capability = ChannelCapability.PRE_AUTH_COMPLETION.getCode()
                .equalsIgnoreCase(request.getTransactionType())
                ? ChannelCapability.PRE_AUTH_COMPLETION
                : ChannelCapability.CAPTURE;
        requireCapability(capability);
        return apiClient.execute(request);
    }

    /**
     * 对普通授权或预授权增加授权额度。
     *
     * @param request 包含原渠道授权号和增量金额的请求
     * @return 包含最新授权状态的标准响应
     **/
    @Override
    public ChannelPaymentResponse incrementalAuthorize(ChannelIncrementalAuthorizeRequest request) {
        requireCapability(ChannelCapability.INCREMENTAL_AUTHORIZATION);
        return apiClient.execute(request);
    }

    /**
     * 发起卡预授权。
     *
     * @param request 包含平台系统交易号、金额和币种的预授权请求
     * @return 包含渠道授权号、授权码及当前状态的标准响应
     **/
    @Override
    public ChannelPaymentResponse preAuthorize(ChannelPreAuthorizeRequest request) {
        requireCapability(ChannelCapability.PRE_AUTHORIZATION);
        return apiClient.execute(request);
    }

    /**
     * 取消普通授权或预授权的全部未请款余额。
     *
     * @param request 包含原渠道授权号的取消请求；渠道协议不接收金额
     * @return 包含取消状态的标准响应
     **/
    @Override
    public ChannelPaymentResponse voidPayment(ChannelVoidRequest request) {
        requireCapability(ChannelCapability.VOID);
        return apiClient.execute(request);
    }

    /**
     * 拒绝冲正请求，因为 ScottChannelA 没有独立冲正端点。
     *
     * @param request 平台冲正请求
     * @return 不会返回
     **/
    @Override
    public ChannelPaymentResponse reversal(ChannelReversalRequest request) {
        throw unsupported(ChannelCapability.REVERSAL);
    }
}
