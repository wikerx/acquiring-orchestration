package com.scott.payment.admin.client.settlement;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalClient
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : service-settlement 结算管理内部客户端边界，隔离 Admin 应用层与 HTTP/HMAC 细节。
 * @status : create
 */
public interface SettlementInternalClient {

    /** @return 批次游标查询结果 */
    BatchSearchResponse search(BatchSearchRequest request);

    /** @return 批次汇率、汇总、净入账和联动详情 */
    BatchDetailResponse detail(String settlementBatchNo);

    /** @return 入账前取消结果 */
    BatchCommandResponse cancel(String settlementBatchNo, InternalBatchCommandRequest request);

    /** @return 已入账批次的独立冲正结果 */
    BatchCommandResponse reverse(String settlementBatchNo, InternalBatchCommandRequest request);
}
