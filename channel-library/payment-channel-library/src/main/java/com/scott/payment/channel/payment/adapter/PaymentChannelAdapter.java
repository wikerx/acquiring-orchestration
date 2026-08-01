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

    /**
     * 返回该旧版适配器唯一支持的渠道编码。
     *
     * @return 平台统一渠道编码
     */
    String supportChannelCode();

    /**
     * 向渠道提交支付请求并映射为平台统一结果。
     *
     * @param request 已完成商户、金额、币种和支付方式校验的渠道请求
     * @return 不直接暴露渠道原始报文的统一支付结果
     */
    PaymentChannelResult submitPayment(PaymentChannelRequest request);

    /**
     * 查询渠道侧支付状态并映射为平台统一结果。
     *
     * @param request 包含平台和渠道交易标识的查询请求
     * @return 统一支付结果；不得直接以渠道状态覆盖平台终态
     */
    PaymentChannelResult queryPayment(PaymentChannelRequest request);

    /**
     * 向渠道提交退款请求并映射为平台统一结果。
     *
     * @param request 包含原交易、退款金额和币种的渠道请求
     * @return 统一退款结果；最终退款状态仍由支付核心状态机确认
     */
    PaymentChannelResult submitRefund(PaymentChannelRequest request);
}
