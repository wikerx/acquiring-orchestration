package com.sinopay.payment.openapi.api.rest.v1.dto.header;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestHeaderDTO
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口请求头数据传输对象
 * @status : create
 */
public class OpenApiRequestHeaderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String merchantId;
    private String appId;
    private String timestamp;
    private String nonce;
    private String signature;

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}

