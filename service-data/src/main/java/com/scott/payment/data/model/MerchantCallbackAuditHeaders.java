package com.scott.payment.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 允许写入通知日志的回调请求头投影，认证令牌始终使用固定占位符。
 *
 * @param authorization 固定脱敏认证头
 * @param contentType 回调正文媒体类型
 * @param callbackVersion 回调协议版本
 * @param callbackTimes 当前投递次数
 * @param callbackEventId 商户幂等事件号
 * @param notifyId 平台通知任务号
 * @param transactionId 平台交易号
 */
public record MerchantCallbackAuditHeaders(
        @JsonProperty("Authorization") String authorization,
        @JsonProperty("Content-Type") String contentType,
        @JsonProperty("X-Callback-Version") String callbackVersion,
        @JsonProperty("X-Callback-Times") String callbackTimes,
        @JsonProperty("X-Callback-Event-Id") String callbackEventId,
        @JsonProperty("X-OPGS-Notify-Id") String notifyId,
        @JsonProperty("X-OPGS-Transaction-Id") String transactionId) {
}
