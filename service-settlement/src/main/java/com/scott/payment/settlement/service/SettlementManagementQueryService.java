package com.scott.payment.settlement.service;

import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchDetailResponse;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSearchRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSearchResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementManagementQueryService
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 结算批次运营查询边界；列表使用有界日期和游标，详情返回汇率、汇总、净入账和异步状态。
 * @status : create
 */
public interface SettlementManagementQueryService {

    /** @param request 有界批次查询条件 @return 主键游标查询结果 */
    BatchSearchResponse search(BatchSearchRequest request);

    /** @param settlementBatchNo 全局结算批次号 @return 批次运营详情 */
    BatchDetailResponse detail(String settlementBatchNo);
}
