package com.scott.payment.payment.service;

import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;

/**
 * 平台 3DS 认证安全审计服务。
 */
public interface PaymentAuthenticationRecordService {

    /**
     * 记录渠道返回的 3DS 或其它支付认证结果。
     *
     * @param request 平台已分配交易身份的渠道认证请求
     * @param response 渠道认证响应；敏感认证正文由实现脱敏或摘要化
     */
    void recordChannelResult(ChannelThreeDsAuthenticationRequest request,
                             ChannelThreeDsAuthenticationResponse response);

    /**
     * 记录渠道认证失败事实，保留可审计的失败码和脱敏摘要。
     *
     * @param request 平台已分配交易身份的渠道认证请求
     * @param status 渠道认证失败或未知状态
     * @param failureCode 渠道认证失败码，只保存受控编码或脱敏摘要
     */
    void recordChannelFailure(ChannelThreeDsAuthenticationRequest request,
                              ChannelThreeDsStatus status,
                              String failureCode);

    /**
     * 记录渠道认证超时事实，供状态机和运营排障使用。
     *
     * @param attemptDO 已超时的 Hosted Checkout 认证尝试事实
     */
    void recordTimeout(PaymentCheckoutAttemptDO attemptDO);
}
