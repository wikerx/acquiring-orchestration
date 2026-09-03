package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordDetailResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecordSearchResponse;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingQueryService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分管理只读查询边界。
 * @status : update
 */
public interface ClearingQueryService {

    /**
     * 在明确单季度半开窗口内执行主键游标查询。
     *
     * @param request 查询筛选、成对游标和有上限页大小
     * @return 当前权威清分修订摘要页
     * @throws IllegalArgumentException 时间窗口跨季度、游标不成对或页大小越界时抛出
     */
    ClearingRecordSearchResponse search(ClearingRecordSearchRequest request);

    /**
     * 按动作交易号和真实季度分片时间查询当前有效修订及原子明细。
     *
     * @param transactionId 动作交易号
     * @param transactionDateTime 动作季度分片时间
     * @return 清分摘要、交易明细和保证金明细
     * @throws IllegalStateException 动作财务状态不存在时抛出
     */
    ClearingRecordDetailResponse detail(String transactionId, LocalDateTime transactionDateTime);
}
