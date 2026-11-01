package com.scott.payment.openapi.support;

/**
 * 渠道回调密钥解析契约，按渠道编码和回调 Header 中的 MID 定位敏感配置；调用方不得记录返回的明文密钥。
 */
@FunctionalInterface
public interface ChannelCallbackSecretResolver {

    /**
     * 解析指定渠道和 MID 的回调签名密钥。
     *
     * @param channelCode 渠道编码
     * @param merchantId 渠道回调中的 MID
     * @return 回调签名密钥；未找到时返回 {@code null}
     */
    String resolve(String channelCode, String merchantId);
}
