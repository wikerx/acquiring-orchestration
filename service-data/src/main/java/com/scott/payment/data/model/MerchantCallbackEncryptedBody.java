package com.scott.payment.data.model;

/**
 * 商户回调加密正文协议对象，字段名与商户回调 v1 契约保持一致。
 *
 * @param data 使用商户响应公钥加密后的紧凑载荷
 */
public record MerchantCallbackEncryptedBody(String data) {
}
