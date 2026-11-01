package com.scott.payment.data.model;

/**
 * 商户回调请求的可持久化审计摘要，不包含密文正文或认证材料。
 *
 * @param data 固定脱敏占位符
 * @param cipherLength 回调密文字符数，用于排查协议截断
 */
public record MerchantCallbackAuditBody(String data, int cipherLength) {
}
