package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecalculateRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRetryRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingReviewRequest;

/** 清分管理命令边界，所有写操作均要求真实分片时间、预期版本、原因和操作人。 */
public interface ClearingManagementCommandService {

    ClearingCommandResponse retry(String transactionId, ClearingRetryRequest request);

    ClearingCommandResponse review(String transactionId, ClearingReviewRequest request);

    ClearingCommandResponse recalculate(String transactionId, ClearingRecalculateRequest request);
}
