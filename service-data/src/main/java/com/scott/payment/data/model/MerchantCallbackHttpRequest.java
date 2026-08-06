package com.scott.payment.data.model;

import org.springframework.http.HttpHeaders;

/** 单次商户回调 HTTP 请求；Authorization 和密文正文禁止写入日志。 */
public record MerchantCallbackHttpRequest(String eventId,
                                          HttpHeaders headers,
                                          String encryptedBody,
                                          String auditBody) {
}
