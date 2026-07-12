package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelCallbackHandler
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 渠道回调处理器骨架，位于 payment-channel-library 渠道实现层，后续按 MPGS 回调签名规则完成验签和事件解析。
 * @status : create
 */
@Component
public class MpgsPaymentChannelCallbackHandler implements PaymentChannelCallbackHandler {

    @Override
    public String channelCode() {
        return PaymentChannelCode.MPGS.getCode();
    }

    @Override
    public ChannelCallbackResult handle(ChannelCallbackRequest request) {
        throw new ChannelRequestException("MPGS渠道回调验签和解析尚未接入，请在交易表落地后补充回调幂等与状态机处理");
    }
}
