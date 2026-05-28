package com.scott.payment.channel.payment.adapter;

import com.scott.payment.channel.payment.model.PaymentChannelRequest;
import com.scott.payment.channel.payment.model.PaymentChannelResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelAdapter
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 收单支付渠道适配器接口
 * @status : create
 */
public interface PaymentChannelAdapter {

    String supportChannelCode();

    PaymentChannelResult submitPayment(PaymentChannelRequest request);

    PaymentChannelResult queryPayment(PaymentChannelRequest request);

    PaymentChannelResult submitRefund(PaymentChannelRequest request);
}
