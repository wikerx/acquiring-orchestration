package com.sinopay.payment.component.security.jwt;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JwtMerchantClaims
 * @date : 2026-05-28 11:42
 * @email : scott_x@163.com
 * @description : 商户 JWT 授权声明
 * @status : create
 */
public class JwtMerchantClaims implements Serializable {

    private static final long serialVersionUID = 1L;

    private String merchantId;
    private String jwtId;
    private long issuedAt;
    private long expiresAt;

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getJwtId() {
        return jwtId;
    }

    public void setJwtId(String jwtId) {
        this.jwtId = jwtId;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(long issuedAt) {
        this.issuedAt = issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
