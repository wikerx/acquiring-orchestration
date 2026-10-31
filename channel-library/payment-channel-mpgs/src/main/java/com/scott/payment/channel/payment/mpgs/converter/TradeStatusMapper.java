package com.scott.payment.channel.payment.mpgs.converter;

import com.scott.payment.channel.payment.mpgs.model.response.ResponsePayload;

import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.springframework.util.StringUtils;

/**
 * MPGS 交易状态映射器，位于 payment-channel-mpgs 渠道实现层，负责按 result 和收单响应码判断渠道交易结果，不处理平台交易状态机。
 */
public class TradeStatusMapper {

    /**
     * MPGS 已完成处理的顶层结果。
     */
    private static final String RESULT_SUCCESS = "SUCCESS";

    /**
     * MPGS 已受理但尚未完成的顶层结果。
     */
    private static final String RESULT_PENDING = "PENDING";

    /**
     * MPGS 无法确认交易结果的顶层状态。
     */
    private static final String RESULT_UNKNOWN = "UNKNOWN";

    /**
     * ISO 8583 收单批准响应码。
     */
    private static final String APPROVED_ACQUIRER_CODE = "00";

    /**
     * 将 MPGS 响应映射为渠道统一状态。
     * <p>
     * MPGS 顶层 result=SUCCESS 只表示网关请求成功受理；授权、支付、请款、退款等交易是否真正成功，
     * 必须以 response.acquirerCode=00 作为核心判断依据，避免把渠道拒付误判为平台成功。
     *
     * @param response MPGS 原始响应
     * @return 渠道统一状态编码
     */
    public String map(ResponsePayload response) {
        if (response == null) {
            return ChannelTradeStatus.PROCESSING.getCode();
        }
        String result = response.getResult();
        if (!StringUtils.hasText(result)) {
            return ChannelTradeStatus.PROCESSING.getCode();
        }
        if (RESULT_SUCCESS.equalsIgnoreCase(result)) {
            return APPROVED_ACQUIRER_CODE.equals(acquirerCode(response))
                    ? ChannelTradeStatus.SUCCESS.getCode()
                    : ChannelTradeStatus.FAILED.getCode();
        }
        if (RESULT_PENDING.equalsIgnoreCase(result)) {
            return ChannelTradeStatus.PENDING.getCode();
        }
        if (RESULT_UNKNOWN.equalsIgnoreCase(result)) {
            return ChannelTradeStatus.PROCESSING.getCode();
        }
        return ChannelTradeStatus.FAILED.getCode();
    }

    /**
     * 读取 MPGS 收单响应码。
     *
     * @param response MPGS 原始响应
     * @return 收单响应码，节点缺失时返回 null
     */
    private String acquirerCode(ResponsePayload response) {
        return response.getResponse() == null ? null : response.getResponse().getAcquirerCode();
    }
}
