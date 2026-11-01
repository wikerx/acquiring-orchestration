package com.scott.payment.openapi.security;

/**
 * 商户密钥获取接口
 */
public interface MerchantKeyProvider {

    /**
     * 获取指定商户当前生效的 JWT HMAC 验签密钥。
     *
     * <p>实现必须按商户隔离密钥，并在密钥缺失、停用或版本不可用时失败关闭。</p>
     *
     * @param merchantId 已从未验签 JWT 中提取、仅用于定位密钥的商户号
     * @return 当前生效的商户 JWT 验签密钥
     */
    String getMerchantKey(String merchantId);
}
