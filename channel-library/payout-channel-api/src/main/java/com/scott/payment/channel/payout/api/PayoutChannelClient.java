package com.scott.payment.channel.payout.api;

import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.enums.PayoutChannelCapability;
import com.scott.payment.channel.payout.exception.PayoutChannelUnsupportedOperationException;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelClient
 * @date : 2026-08-12 00:00
 * @description : 代付渠道 SPI，独立定义提交与查询能力，不复用收单支付接口或模型。
 * @status : create
 */
public interface PayoutChannelClient {

    /** @return 当前 Provider 唯一渠道编码 */
    String channelCode();

    /** @return 当前 Provider 明确支持的代付能力 */
    Set<PayoutChannelCapability> capabilities();

    /** 判断当前 Provider 是否支持指定代付能力。 */
    default boolean supports(PayoutChannelCapability capability) {
        return capabilities() != null && capabilities().contains(capability);
    }

    /** 提交一笔代付；平台状态仍由 service-payout 状态机决定。 */
    default ChannelPayoutResponse submit(ChannelPayoutRequest request) {
        throw unsupported(PayoutChannelCapability.SUBMIT);
    }

    /** 查询渠道侧代付状态；不得直接用渠道原始状态覆盖平台终态。 */
    default ChannelPayoutResponse query(ChannelPayoutQueryRequest request) {
        throw unsupported(PayoutChannelCapability.QUERY);
    }

    /** 构造统一的不支持能力异常。 */
    default PayoutChannelUnsupportedOperationException unsupported(PayoutChannelCapability capability) {
        return new PayoutChannelUnsupportedOperationException(channelCode(), capability.getCode());
    }
}
