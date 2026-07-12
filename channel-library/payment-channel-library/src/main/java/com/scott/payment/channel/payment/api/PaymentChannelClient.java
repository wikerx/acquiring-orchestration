package com.scott.payment.channel.payment.api;

import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelCaptureRequest;
import com.scott.payment.channel.payment.dto.request.ChannelIncrementalAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.request.ChannelPreAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelRefundRequest;
import com.scott.payment.channel.payment.dto.request.ChannelReversalRequest;
import com.scott.payment.channel.payment.dto.request.ChannelVoidRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelUnsupportedOperationException;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道客户端 SPI，位于 payment-channel-library API 层，用于统一授权、支付、请款、退款、撤销、冲正和查询等渠道能力。
 * @status : create
 */
public interface PaymentChannelClient {

    /**
     * 获取渠道编码。
     *
     * @return 渠道编码
     */
    String channelCode();

    /**
     * 获取渠道支持的交易能力。
     *
     * @return 渠道能力集合
     */
    Set<ChannelCapability> capabilities();

    /**
     * 判断渠道是否支持指定能力。
     *
     * @param capability 渠道能力
     * @return true 表示支持
     */
    default boolean supports(ChannelCapability capability) {
        return capabilities() != null && capabilities().contains(capability);
    }

    /**
     * 提交一步支付交易。
     *
     * @param request 渠道支付请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse payment(ChannelPaymentRequest request) {
        throw unsupported(ChannelCapability.PAYMENT);
    }

    /**
     * 提交授权交易。
     *
     * @param request 渠道授权请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse authorize(ChannelAuthorizeRequest request) {
        throw unsupported(ChannelCapability.AUTHORIZATION);
    }

    /**
     * 提交预授权交易。
     *
     * @param request 渠道预授权请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse preAuthorize(ChannelPreAuthorizeRequest request) {
        throw unsupported(ChannelCapability.PRE_AUTHORIZATION);
    }

    /**
     * 提交增量授权交易。
     *
     * @param request 渠道增量授权请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse incrementalAuthorize(ChannelIncrementalAuthorizeRequest request) {
        throw unsupported(ChannelCapability.INCREMENTAL_AUTHORIZATION);
    }

    /**
     * 提交请款交易。
     *
     * @param request 渠道请款请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse capture(ChannelCaptureRequest request) {
        throw unsupported(ChannelCapability.CAPTURE);
    }

    /**
     * 提交退款交易。
     *
     * @param request 渠道退款请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse refund(ChannelRefundRequest request) {
        throw unsupported(ChannelCapability.REFUND);
    }

    /**
     * 提交撤销交易。
     *
     * @param request 渠道撤销请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse voidPayment(ChannelVoidRequest request) {
        throw unsupported(ChannelCapability.VOID);
    }

    /**
     * 提交冲正交易。
     *
     * @param request 渠道冲正请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse reversal(ChannelReversalRequest request) {
        throw unsupported(ChannelCapability.REVERSAL);
    }

    /**
     * 查询渠道交易。
     *
     * @param request 渠道查询请求
     * @return 渠道统一响应
     */
    default ChannelPaymentResponse query(ChannelQueryRequest request) {
        throw unsupported(ChannelCapability.QUERY);
    }

    /**
     * 构造渠道不支持能力异常。
     *
     * @param capability 渠道能力
     * @return 渠道不支持能力异常
     */
    default ChannelUnsupportedOperationException unsupported(ChannelCapability capability) {
        return new ChannelUnsupportedOperationException(channelCode(), capability.getCode());
    }
}
