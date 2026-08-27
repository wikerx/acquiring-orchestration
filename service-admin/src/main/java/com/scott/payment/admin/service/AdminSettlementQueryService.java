package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementQueryService
 * @date : 2026-08-27 14:10
 * @email : scott_x@163.com
 * @description : Admin 结算只读查询边界；直接读取交易逻辑数据源，不承担取消、冲正或资金入账。
 * @status : create
 */
public interface AdminSettlementQueryService {

    /** @return 按业务日期和主键倒序的结算批次标准分页 */
    PageResult<BatchSummary> search(BatchSearchRequest request);

    /** @return 批次、锁定汇率、结果汇总、净入账与异步联动状态 */
    BatchDetailResponse detail(String settlementBatchNo);
}
