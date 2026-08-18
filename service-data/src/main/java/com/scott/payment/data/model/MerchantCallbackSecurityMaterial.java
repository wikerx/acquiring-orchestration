package com.scott.payment.data.model;

/** 商户回调所需的内存安全材料；禁止序列化、缓存或写入日志。 */
public final class MerchantCallbackSecurityMaterial {

    /** 商户当前有效的回调 JWT HMAC 密钥，敏感且不允许为空。 */
    private final String jwtSecret;
    /** 商户响应公钥，用于加密回调正文，敏感且不允许为空。 */
    private final String responsePublicKey;

    /**
     * 创建一次回调请求范围内使用的安全材料。
     *
     * @param jwtSecret 商户当前有效的回调 JWT HMAC 密钥
     * @param responsePublicKey 商户响应公钥
     */
    public MerchantCallbackSecurityMaterial(String jwtSecret, String responsePublicKey) {
        this.jwtSecret = jwtSecret;
        this.responsePublicKey = responsePublicKey;
    }

    /**
     * 返回 JWT HMAC 密钥；调用方不得记录或持久化该值。
     *
     * @return 商户回调 JWT HMAC 密钥
     */
    public String getJwtSecret() {
        return jwtSecret;
    }

    /**
     * 返回商户响应公钥。
     *
     * @return X.509 编码的商户响应公钥
     */
    public String getResponsePublicKey() {
        return responsePublicKey;
    }
}
