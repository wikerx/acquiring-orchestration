package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.security.HmacPaymentChannelCallbackVerifier;

import java.util.Set;

/** MPGS 回调验签实现；当前沿用平台 HMAC/Event-Signature 契约，后续可在本模块替换为官方格式。 */
public class MpgsCallbackVerifier extends HmacPaymentChannelCallbackVerifier {

    public MpgsCallbackVerifier() {
        super(Set.of("MPGS"));
    }
}
