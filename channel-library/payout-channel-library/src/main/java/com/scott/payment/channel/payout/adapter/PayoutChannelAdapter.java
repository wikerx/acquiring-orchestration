package com.scott.payment.channel.payout.adapter;

import com.scott.payment.channel.payout.model.PayoutChannelRequest;
import com.scott.payment.channel.payout.model.PayoutChannelResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelAdapter
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 代付渠道适配器接口
 * @status : create
 */
public interface PayoutChannelAdapter {

    String supportChannelCode();

    PayoutChannelResult submitPayout(PayoutChannelRequest request);

    PayoutChannelResult queryPayout(PayoutChannelRequest request);
}
