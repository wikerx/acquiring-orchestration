package com.scott.payment.channel.payout.api;

import com.scott.payment.channel.payout.dto.request.ChannelPayoutQueryRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutCancelRequest;
import com.scott.payment.channel.payout.dto.request.ChannelPayoutRequest;
import com.scott.payment.channel.payout.dto.response.ChannelPayoutResponse;
import com.scott.payment.channel.payout.enums.PayoutChannelCapability;
import com.scott.payment.channel.payout.exception.PayoutChannelUnsupportedOperationException;

import java.util.Set;

/**
 * 代付渠道 SPI，独立定义提交与查询能力，不复用收单支付接口或模型。
 */
public interface PayoutChannelClient {

    /**
     * 返回当前 Provider 的唯一渠道编码。
     *
     * @return 稳定渠道编码
     */
    String channelCode();

    /**
     * 返回当前 Provider 明确支持的代付能力。
     *
     * @return 不可变或只读能力集合
     */
    Set<PayoutChannelCapability> capabilities();

    /**
     * 判断当前 Provider 是否支持指定代付能力。
     */
    default boolean supports(PayoutChannelCapability capability) {
        return capabilities() != null && capabilities().contains(capability);
    }

    /**
     * 提交一笔代付；平台状态仍由 service-payout 状态机决定。
     */
    default ChannelPayoutResponse submit(ChannelPayoutRequest request) {
        throw unsupported(PayoutChannelCapability.SUBMIT);
    }

    /**
     * 查询渠道侧代付状态；不得直接用渠道原始状态覆盖平台终态。
     */
    default ChannelPayoutResponse query(ChannelPayoutQueryRequest request) {
        throw unsupported(PayoutChannelCapability.QUERY);
    }

    /**
     * 取消仍处于渠道处理中状态的代付；平台状态机仍由 service-payout 决定。
     *
     * @param request 代付取消请求
     * @return 渠道统一响应
     */
    default ChannelPayoutResponse cancel(ChannelPayoutCancelRequest request) {
        throw unsupported(PayoutChannelCapability.CANCEL);
    }

    /**
     * 构造统一的不支持能力异常。
     */
    default PayoutChannelUnsupportedOperationException unsupported(PayoutChannelCapability capability) {
        return new PayoutChannelUnsupportedOperationException(channelCode(), capability.getCode());
    }
}
