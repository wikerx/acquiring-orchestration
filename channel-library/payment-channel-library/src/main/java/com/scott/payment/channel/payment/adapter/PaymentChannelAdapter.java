package com.scott.payment.channel.payment.adapter;

import com.scott.payment.channel.payment.model.PaymentChannelRequest;
import com.scott.payment.channel.payment.model.PaymentChannelResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelAdapter
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 收单支付旧版渠道适配器接口，位于 payment-channel-library 渠道适配层，保留用于兼容早期调用；新接入渠道应优先使用 PaymentChannelClient SPI。
 * @status : create
 */
@Deprecated
public interface PaymentChannelAdapter {

    String supportChannelCode();

    PaymentChannelResult submitPayment(PaymentChannelRequest request);

    PaymentChannelResult queryPayment(PaymentChannelRequest request);

    PaymentChannelResult submitRefund(PaymentChannelRequest request);
}
