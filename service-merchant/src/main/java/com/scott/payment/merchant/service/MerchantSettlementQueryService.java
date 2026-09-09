package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementQueryService
 * @date : 2026-09-01 22:35
 * @email : scott_x@163.com
 * @description : service-merchant 基于本地交易逻辑数据源查询结算账单与逐笔明细的只读边界；每个方法都必须强制携带认证商户号。
 * @status : update
 */
public interface MerchantSettlementQueryService {

    /**
     * 分页查询商户已入账或已冲正的结算批次。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param query 批次号、类型、状态、业务日期和分页条件
     * @return 当前商户范围内的批次分页
     */
    PageResult<BatchSummary> searchBatches(String merchantId, BatchQuery query);

    /**
     * 查询商户指定批次详情。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param settlementBatchNo 结算批次号
     * @return 批次、锁定汇率和结果汇总
     */
    BatchDetail getBatch(String merchantId, String settlementBatchNo);

    /**
     * 分页查询批次内具有真实来源交易号的财务组件。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param query 交易结算明细过滤和分页条件
     * @return 当前商户范围内的真实交易结算明细
     */
    PageResult<TransactionItem> searchTransactionItems(String merchantId, TransactionItemQuery query);

    /**
     * 分页查询保证金动作及动作后的责任快照。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param query 保证金编号、来源交易、动作、币种、业务日期和分页条件
     * @return 当前商户范围内的保证金结算明细
     */
    PageResult<ReserveItem> searchReserveItems(String merchantId, ReserveItemQuery query);
}
