package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayJsonPaymentChannelClient
 * @date : 2026-07-19 22:50
 * @email : scott_x@163.com
 * @description : WorldPay JSON 收单渠道客户端，位于 payment-channel-library 渠道实现层，仅代表独立渠道 WPGJSON 的 SPI 占位入口；当前尚未实现 JSON 报文构造、渠道认证、HTTP 调用和响应解析，禁止按已接通生产交易理解。
 * @status : create
 */
@Component
public class WorldPayJsonPaymentChannelClient extends AbstractWorldPayPaymentChannelClient {

    /**
     * 获取 WorldPay JSON 渠道编码。
     *
     * @return 渠道编码
     */
    @Override
    public String channelCode() {
        return PaymentChannelCode.WPGJSON.getCode();
    }

    /**
     * 执行 WorldPay JSON 渠道请求。
     * <p>
     * 当前方法故意抛出“未接通”异常，避免系统配置了 WPGJSON 后误发真实交易；后续正式接入时应在该类内实现
     * WPG JSON Payment、Capture、Refund、Cancel 和 Inquiry 请求/响应，不和 WPGXML 共用协议实现。
     *
     * @param request 渠道统一请求
     * @return 渠道统一响应
     */
    @Override
    protected ChannelPaymentResponse execute(ChannelPaymentRequest request) {
        throw new WorldPayChannelNotImplementedException(channelCode(), request == null ? null : request.getTransactionType());
    }
}
