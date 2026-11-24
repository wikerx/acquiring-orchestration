package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** OpenAPI 观测到的单次渠道回调 HTTP 应答。正文只允许留存已批准的固定值。 */
public record CallbackDeliveryResponseCommand(
        @NotNull @Pattern(regexp = "[a-f0-9]{32}") String deliveryId,
        @Min(100) @Max(599) int httpStatus,
        @Pattern(regexp = "SUCCESS") String responseBody,
        @Min(0) long durationMillis) {
}
