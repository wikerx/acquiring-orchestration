package com.scott.payment.admin.client.clearing;

import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalRecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplayReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplaySubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayResponse;

import java.time.LocalDateTime;

/** service-clearing 内部管理客户端。 */
public interface ClearingInternalClient {
    SearchResponse search(SearchRequest request);
    DetailResponse detail(String transactionId, LocalDateTime transactionDateTime);
    CommandResponse retry(String transactionId, InternalActionRequest request);
    CommandResponse review(String transactionId, InternalActionRequest request);
    CommandResponse recalculate(String transactionId, InternalRecalculateRequest request);
    ReserveAdjustmentResponse submitReserveAdjustment(InternalReserveAdjustmentSubmitRequest request);
    ReserveAdjustmentResponse reviewReserveAdjustment(
            String adjustmentNo, InternalReserveAdjustmentReviewRequest request);
    TierPeriodReplayResponse submitTierPeriodReplay(InternalTierPeriodReplaySubmitRequest request);
    TierPeriodReplayResponse reviewTierPeriodReplay(
            String replayNo, InternalTierPeriodReplayReviewRequest request);
}
