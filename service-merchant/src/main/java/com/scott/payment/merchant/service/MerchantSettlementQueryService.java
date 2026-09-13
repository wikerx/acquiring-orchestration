package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ClearingDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReconciliationRecord;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.SummaryLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionSettlement;

import java.time.LocalDateTime;
import java.util.List;

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

    /** @return 当前认证商户指定批次的完整凭证快照 */
    BatchDetail getVoucher(String merchantId, String settlementBatchNo);

    /** @return 当前认证商户指定批次的结算汇总标准分页 */
    PageResult<SummaryLine> searchResultSummaries(String merchantId,
                                                  String settlementBatchNo,
                                                  Integer pageNo,
                                                  Integer pageSize);

    /**
     * 分页查询批次内具有真实来源交易号的财务组件。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param query 交易结算明细过滤和分页条件
     * @return 当前商户范围内的真实交易结算明细
     */
    PageResult<TransactionSettlement> searchTransactionItems(String merchantId, TransactionItemQuery query);

    /** 分页读取当前商户正式批次内一笔交易的结算财务组件。 */
    PageResult<TransactionItem> searchTransactionComponents(String merchantId,
                                                            String settlementBatchNo,
                                                            String transactionId,
                                                            Integer pageNo,
                                                            Integer pageSize);

    /**
     * 按真实交易身份查询当前认证商户的对账、结算和入账状态。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param transactionId 真实平台交易号
     * @param transactionDateTime 交易季度精确路由时间
     * @return 当前商户匹配的对账记录，未匹配时为空集合
     */
    List<ReconciliationRecord> findReconciliationRecordsByTransaction(
            String merchantId,
            String transactionId,
            LocalDateTime transactionDateTime);

    /**
     * 按真实交易身份查询当前认证商户的清分摘要和原子行。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param transactionId 真实平台交易号
     * @param transactionDateTime 交易季度精确路由时间
     * @return 当前商户范围内的清分详情，尚未产生清分记录时返回 {@code null}
     */
    ClearingDetail findClearingDetailByTransaction(
            String merchantId,
            String transactionId,
            LocalDateTime transactionDateTime);

    /**
     * 按真实交易身份查询跨批次结算财务组件，不受列表业务日期窗口限制。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param transactionId 真实平台交易号
     * @param transactionDateTime 交易季度精确路由时间
     * @param pageNo 页码，从 1 开始
     * @param pageSize 页大小，受查询预算限制
     * @return 当前商户范围内的交易结算历史
     */
    PageResult<TransactionItem> searchTransactionItemsByTransaction(String merchantId,
                                                                    String transactionId,
                                                                    LocalDateTime transactionDateTime,
                                                                    Integer pageNo,
                                                                    Integer pageSize);

    /**
     * 分页查询保证金动作及动作后的责任快照。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param query 保证金编号、来源交易、动作、币种、业务日期和分页条件
     * @return 当前商户范围内的保证金结算明细
     */
    PageResult<ReserveItem> searchReserveItems(String merchantId, ReserveItemQuery query);

    /**
     * 按原支付交易身份查询跨批次保证金动作，不受列表业务日期窗口限制。
     *
     * @param merchantId 可信认证上下文中的商户号
     * @param transactionId 原支付真实平台交易号
     * @param transactionDateTime 原支付季度精确路由时间
     * @param pageNo 页码，从 1 开始
     * @param pageSize 页大小，受查询预算限制
     * @return 当前商户范围内的保证金动作历史
     */
    PageResult<ReserveItem> searchReserveItemsByTransaction(String merchantId,
                                                            String transactionId,
                                                            LocalDateTime transactionDateTime,
                                                            Integer pageNo,
                                                            Integer pageSize);
}
