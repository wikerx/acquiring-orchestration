package com.scott.payment.channel.payment.scottchannela.converter;

/**
 * 绑定一次渠道操作的端点和强类型请求体，避免端点选择与 Payload 构造发生语义偏差。
 *
 * @param path 渠道 API 版本之后的操作路径
 * @param payload 与路径匹配的渠道请求对象
 **/
public record PreparedPaymentRequest(String path, Object payload) {
}
