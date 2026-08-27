package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordDetailResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchResponse;

import java.time.LocalDateTime;

/** 清分管理只读查询边界。 */
public interface ClearingQueryService {

    /** 在一个明确季度的半开时间窗口中执行主键游标查询。 */
    ClearingRecordSearchResponse search(ClearingRecordSearchRequest request);

    /** 按动作交易号和真实季度分片时间查询当前有效修订。 */
    ClearingRecordDetailResponse detail(String transactionId, LocalDateTime transactionDateTime);
}
