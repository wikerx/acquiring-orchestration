package com.sinopay.payment.openapi.security;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyProvider
 * @date : 2026-05-28 11:42
 * @email : scott_x@163.com
 * @description : 商户密钥获取接口
 * @status : create
 */
public interface MerchantKeyProvider {

    String getMerchantKey(String merchantId);
}
