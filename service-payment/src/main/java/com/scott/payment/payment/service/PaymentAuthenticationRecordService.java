package com.scott.payment.payment.service;

import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.dto.response.ChannelThreeDsAuthenticationResponse;
import com.scott.payment.channel.payment.enums.ChannelThreeDsStatus;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;

/** 平台 3DS 认证安全审计服务。 */
public interface PaymentAuthenticationRecordService {

    void recordChannelResult(ChannelThreeDsAuthenticationRequest request,
                             ChannelThreeDsAuthenticationResponse response);

    void recordChannelFailure(ChannelThreeDsAuthenticationRequest request,
                              ChannelThreeDsStatus status,
                              String failureCode);

    void recordTimeout(PaymentCheckoutAttemptDO attemptDO);
}
