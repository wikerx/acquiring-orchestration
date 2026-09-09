package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.security.HmacPaymentChannelCallbackVerifier;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayCallbackVerifier
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Worldpay JSON/XML 回调验签实现；签名算法与密钥选择限制在 Worldpay provider 模块。
 * @status : create
 */
public class WorldPayCallbackVerifier extends HmacPaymentChannelCallbackVerifier {

    public WorldPayCallbackVerifier() {
        super(Set.of(WorldPayChannelCode.WPGJSON, WorldPayChannelCode.WPGXML));
    }
}
