package com.scott.payment.payment.service;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTransactionStatusResolver
 * @date : 2026-07-19 22:00
 * @email : scott_x@163.com
 * @description : 渠道结果状态解析服务，位于 service-payment 服务层，用于按渠道、交易类型和渠道原始状态决定平台交易状态；不负责渠道 HTTP 调用、报文签名或真实渠道接通。
 * @status : create
 */
public interface ChannelTransactionStatusResolver {

    /**
     * 解析渠道同步响应对应的平台交易状态。
     * <p>
     * 对 WPGXML/WPGJSON，一步支付和请款同步 AUTHORISED 只能解析为等待回调/查询，不能直接按成功终态处理。
     *
     * @param channelCode 渠道编码
     * @param transactionType 平台交易类型
     * @param response 渠道同步响应
     * @return 平台状态解析结果
     */
    ChannelTransactionStatusResolution resolveSync(String channelCode,
                                                   String transactionType,
                                                   ChannelPaymentResponse response);

    /**
     * 解析渠道回调或查询结果对应的平台交易状态。
     * <p>
     * 该方法只输出目标状态建议，最终能否推进终态仍由交易记录服务通过 CAS 和终态保护控制。
     *
     * @param channelCode 渠道编码
     * @param transactionType 平台交易类型
     * @param callbackResult 渠道回调或查询结果
     * @return 平台状态解析结果
     */
    ChannelTransactionStatusResolution resolveCallback(String channelCode,
                                                       String transactionType,
                                                       ChannelCallbackResult callbackResult);
}
