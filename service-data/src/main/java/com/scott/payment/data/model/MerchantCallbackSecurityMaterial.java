package com.scott.payment.data.model;

/** 商户回调所需的内存安全材料；禁止序列化、缓存或写入日志。 */
public final class MerchantCallbackSecurityMaterial {

    private final String jwtSecret;
    private final String responsePublicKey;

    public MerchantCallbackSecurityMaterial(String jwtSecret, String responsePublicKey) {
        this.jwtSecret = jwtSecret;
        this.responsePublicKey = responsePublicKey;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public String getResponsePublicKey() {
        return responsePublicKey;
    }
}
