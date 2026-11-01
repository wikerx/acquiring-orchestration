package com.scott.payment.data.model;

import org.springframework.http.HttpHeaders;

/**
 * 单次商户回调 HTTP 请求；Authorization 和密文正文禁止写入日志。
 *
 * @param eventId 商户侧用于幂等处理的稳定回调事件号
 * @param headers 包含认证材料的实际 HTTP 请求头，只允许传给 HTTP 客户端
 * @param encryptedBody 使用商户响应公钥加密后的实际请求正文
 * @param auditBody 不包含认证材料或密文原文的请求审计摘要
 */
public record MerchantCallbackHttpRequest(String eventId,
                                          HttpHeaders headers,
                                          String encryptedBody,
                                          String auditBody) {
}
