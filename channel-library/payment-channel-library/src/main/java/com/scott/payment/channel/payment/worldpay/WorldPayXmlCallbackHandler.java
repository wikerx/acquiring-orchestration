package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayXmlCallbackHandler
 * @date : 2026-07-19 23:00
 * @email : scott_x@163.com
 * @description : WorldPay XML 回调处理器，位于 payment-channel-library 渠道实现层，仅负责 WPGXML 基础通知字段解析和状态归一；生产使用前仍必须补齐 WorldPay 签名/认证、IP 白名单和真实通知样例校验。
 * @status : create
 */
@Component
public class WorldPayXmlCallbackHandler extends AbstractWorldPayCallbackHandler {

    /**
     * 获取渠道编码。
     *
     * @return WPGXML
     */
    @Override
    public String channelCode() {
        return PaymentChannelCode.WPGXML.getCode();
    }
}
