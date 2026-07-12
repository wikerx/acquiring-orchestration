package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsTradeStatusMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 交易状态映射器，位于 payment-channel-library 渠道实现层，负责按 result 和收单响应码判断渠道交易结果，不处理平台交易状态机。
 * @status : create
 */
public class MpgsTradeStatusMapper {

    private static final String RESULT_SUCCESS = "SUCCESS";

    private static final String RESULT_PENDING = "PENDING";

    private static final String RESULT_UNKNOWN = "UNKNOWN";

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
    public String map(MpgsResponsePayload response) {
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

    private String acquirerCode(MpgsResponsePayload response) {
        return response.getResponse() == null ? null : response.getResponse().getAcquirerCode();
    }
}
