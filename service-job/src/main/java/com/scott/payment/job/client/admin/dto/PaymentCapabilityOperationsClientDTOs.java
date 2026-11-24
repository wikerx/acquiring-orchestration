package com.scott.payment.job.client.admin.dto;

import java.time.LocalDateTime;

/** 支付方式巡检的归属服务协议。 */
public final class PaymentCapabilityOperationsClientDTOs {

    private PaymentCapabilityOperationsClientDTOs() {
    }

    public record Cursor(LocalDateTime time, long sourceId) {
    }

    public record BoundaryRequest(String source, Cursor after, LocalDateTime through, int limit,
                                  long deadlineEpochMillis) {
    }

    public record BoundaryResponse(Cursor next, boolean hasMore, int processed) {
    }

    public record RecoveryRequest(int limit, long deadlineEpochMillis) {
    }

    public record RecoveryResponse(int requeued, int relayed, Integer selected, Integer failed,
                                   Integer deferred, Boolean budgetExhausted) {
    }
}
