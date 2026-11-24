package com.scott.payment.openapi.client.payment.dto;

/** 单次回调的 HTTP 状态、可留存的固定应答正文及入口耗时。 */
public record CallbackDeliveryResponseRequest(String deliveryId, int httpStatus, String responseBody,
                                              long durationMillis) {
}
