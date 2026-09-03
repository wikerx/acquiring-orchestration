package com.scott.payment.data.model;

import org.springframework.http.HttpHeaders;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackHttpRequest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 单次商户回调 HTTP 请求；Authorization 和密文正文禁止写入日志。
 * @status : create
 */
public record MerchantCallbackHttpRequest(String eventId,
                                          HttpHeaders headers,
                                          String encryptedBody,
                                          String auditBody) {
}
