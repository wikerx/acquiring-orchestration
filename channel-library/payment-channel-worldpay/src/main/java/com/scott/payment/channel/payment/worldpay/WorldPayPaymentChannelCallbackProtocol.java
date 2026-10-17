package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackAcknowledgement;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackProtocolDescriptor;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.VerifiedChannelEvent;
import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayPaymentChannelCallbackProtocol
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : WorldPay 回调协议占位实现，位于渠道适配层；官方认证报文和测试夹具接入前声明 UNSUPPORTED 并统一失败关闭。
 * @status : create
 */
public class WorldPayPaymentChannelCallbackProtocol implements PaymentChannelCallbackProtocol {

    private static final ChannelCallbackProtocolDescriptor DESCRIPTOR =
            new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"),
                    Set.of("application/json", "application/xml", "text/xml"),
                    Set.of(ChannelCallbackScene.STANDARD),
                    512 * 1024,
                    ChannelCallbackAuthentication.UNSUPPORTED);

    /** {@inheritDoc} */
    @Override
    public Set<String> channelCodes() {
        return Set.of(WorldPayChannelCode.WPGJSON, WorldPayChannelCode.WPGXML);
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCallbackProtocolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /** {@inheritDoc} */
    @Override
    public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                               ChannelCallbackVerificationContext context) {
        throw new ChannelCallbackVerificationException(
                ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED,
                "WorldPay callback authentication is not production ready");
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                  VerifiedChannelEvent event) {
        return ChannelCallbackAcknowledgement.platformSuccess(request.channelCode() + " accepted");
    }
}
