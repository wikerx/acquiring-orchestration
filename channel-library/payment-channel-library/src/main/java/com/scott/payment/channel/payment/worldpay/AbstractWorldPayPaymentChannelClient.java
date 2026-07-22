package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.api.AbstractPaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AbstractWorldPayPaymentChannelClient
 * @date : 2026-07-19 22:50
 * @email : scott_x@163.com
 * @description : WorldPay 收单渠道客户端抽象基类，位于 payment-channel-library 渠道实现层，只复用 WPGXML/WPGJSON 的计划能力分发和 SPI 入口约束；当前不承载真实渠道请求，真实 XML 与 JSON 协议实现必须分别落在各自独立渠道客户端中。
 * @status : create
 */
public abstract class AbstractWorldPayPaymentChannelClient extends AbstractPaymentChannelClient {

    /**
     * 获取 WorldPay 计划接入的渠道能力。
     * <p>
     * 这里声明的是平台路由可识别的“计划能力”，用于后续接入时保持渠道能力模型完整；它不代表 WPGXML/WPGJSON
     * 的真实 HTTP 请求、渠道认证、签名或响应解析已经接通。当前具体渠道类的 execute 会抛出明确异常，防止误用于生产扣款、请款、退款或查询。
     *
     * @return 渠道能力集合
     */
    @Override
    public Set<ChannelCapability> capabilities() {
        return EnumSet.of(
                ChannelCapability.PAYMENT,
                ChannelCapability.AUTHORIZATION,
                ChannelCapability.CAPTURE,
                ChannelCapability.PRE_AUTHORIZATION,
                ChannelCapability.PRE_AUTH_COMPLETION,
                ChannelCapability.REFUND,
                ChannelCapability.VOID,
                ChannelCapability.QUERY
        );
    }

    /**
     * 提交 WorldPay 一步支付请求。
     * <p>
     * 当前抽象层只做计划能力校验和请求分发；WPGXML/WPGJSON 子类在真实 API 未接通前会通过 execute 抛出未实现异常。
     *
     * @param request 渠道一步支付请求
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse payment(ChannelPaymentRequest request) {
        requireCapability(ChannelCapability.PAYMENT);
        return execute(request);
    }

    /**
     * 提交 WorldPay 授权请求。
     * <p>
     * 该方法不构造 WorldPay 报文，也不决定 AUTHORISED 是否为平台终态，只把请求交给独立协议子类。
     *
     * @param request 渠道授权请求
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse authorize(ChannelAuthorizeRequest request) {
        requireCapability(ChannelCapability.AUTHORIZATION);
        return execute(request);
    }

    /**
     * 提交 WorldPay 预授权请求。
     *
     * @param request 渠道预授权请求
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse preAuthorize(ChannelPreAuthorizeRequest request) {
        requireCapability(ChannelCapability.PRE_AUTHORIZATION);
        return execute(request);
    }

    /**
     * 提交 WorldPay 增量授权请求。
     *
     * @param request 渠道增量授权请求
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse incrementalAuthorize(ChannelIncrementalAuthorizeRequest request) {
        requireCapability(ChannelCapability.INCREMENTAL_AUTHORIZATION);
        return execute(request);
    }

    /**
     * 提交 WorldPay 请款或预授权完成请求。
     * <p>
     * WorldPay 请款同步 AUTHORISED 不代表平台请款成功，平台成功终态必须等待 CAPTURED/SETTLED 回调或查询勾兑。
     *
     * @param request 渠道请款请求
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse capture(ChannelCaptureRequest request) {
        requireCapability(ChannelCapability.CAPTURE);
        return execute(request);
    }

    /**
     * 提交 WorldPay 退款请求。
     *
     * @param request 渠道退款请求
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse refund(ChannelRefundRequest request) {
        requireCapability(ChannelCapability.REFUND);
        return execute(request);
    }

    /**
     * 提交 WorldPay 撤销请求。
     *
     * @param request 渠道撤销请求
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse voidPayment(ChannelVoidRequest request) {
        requireCapability(ChannelCapability.VOID);
        return execute(request);
    }

    /**
     * 查询 WorldPay 交易状态。
     * <p>
     * 查询用于自动勾兑确认终态；当前 WPGXML/WPGJSON Inquiry 未实现前，子类必须继续抛出未接通异常。
     *
     * @param request 渠道查询请求
     * @return 渠道统一响应
     */
    @Override
    public ChannelPaymentResponse query(ChannelQueryRequest request) {
        requireCapability(ChannelCapability.QUERY);
        return execute(request);
    }

    /**
     * 执行 WorldPay 渠道请求。
     * <p>
     * 子类必须按独立渠道协议实现：WPGXML 使用 XML 报文和 XML 响应解析，WPGJSON 使用 JSON 报文和 JSON 响应解析；
     * 该抽象层不得承载平台交易状态机，也不得把渠道 AUTHORISED/CAPTURED 直接写入平台交易表。
     *
     * @param request 渠道统一请求
     * @return 渠道统一响应
     */
    protected abstract ChannelPaymentResponse execute(ChannelPaymentRequest request);
}
