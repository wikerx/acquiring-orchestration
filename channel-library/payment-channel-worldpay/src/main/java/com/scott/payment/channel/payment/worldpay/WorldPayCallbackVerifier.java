package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.security.HmacPaymentChannelCallbackVerifier;

import java.util.Set;

/** Worldpay JSON/XML 回调验签实现；签名算法与密钥选择限制在 Worldpay provider 模块。 */
public class WorldPayCallbackVerifier extends HmacPaymentChannelCallbackVerifier {

    public WorldPayCallbackVerifier() {
        super(Set.of(WorldPayChannelCode.WPGJSON, WorldPayChannelCode.WPGXML));
    }
}
