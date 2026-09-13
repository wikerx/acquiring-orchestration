package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReconciliationRecord;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
import com.scott.payment.component.core.model.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReportingQueryService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 结算结果和净额入账流水的本地只读查询边界。
 * @status : create
 */
public interface AdminSettlementReportingQueryService {

    /**
     * 分页查询正式结算批次的交易结果明细。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<TransactionSettlementSummary> searchResultItems(ResultItemSearchRequest request,
                                                               AdminMerchantDataScope dataScope);

    /** 分页读取正式批次内一笔交易的不可变结算财务组件。 */
    PageResult<ResultItemSummary> searchResultItemComponents(String settlementBatchNo,
                                                             String transactionId,
                                                             Integer pageNo,
                                                             Integer pageSize,
                                                             AdminMerchantDataScope dataScope);

    /**
     * 按真实交易身份查询交易动作上的对账、结算和入账状态。
     *
     * @param transactionId 真实平台交易号
     * @param transactionDateTime 交易季度精确路由时间
     * @param dataScope 可信登录上下文解析出的商户数据范围
     * @return 数据范围内匹配的对账记录，未匹配时为空集合
     */
    List<ReconciliationRecord> findReconciliationRecordsByTransaction(
            String transactionId,
            LocalDateTime transactionDateTime,
            AdminMerchantDataScope dataScope);

    /**
     * 按真实交易号和交易时间分页查询该动作对应的正式结算结果。
     *
     * @param transactionId 真实平台交易号
     * @param transactionDateTime 交易季度精确路由时间
     * @param pageNo 页码，从 1 开始
     * @param pageSize 页大小，受查询预算限制
     * @param dataScope 可信登录上下文解析出的商户数据范围
     * @return 当前交易的结算结果分页
     */
    PageResult<ResultItemSummary> searchResultItemsByTransaction(String transactionId,
                                                                 LocalDateTime transactionDateTime,
                                                                 Integer pageNo,
                                                                 Integer pageSize,
                                                                 AdminMerchantDataScope dataScope);

    /**
     * 分页查询正式结算批次的保证金结算明细。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<ReserveItemSummary> searchReserveItems(ReserveItemSearchRequest request,
                                                      AdminMerchantDataScope dataScope);

    /**
     * 按原支付交易号和原支付时间分页查询完整保证金动作历史。
     *
     * @param transactionId 原支付真实平台交易号
     * @param transactionDateTime 原支付季度精确路由时间
     * @param pageNo 页码，从 1 开始
     * @param pageSize 页大小，受查询预算限制
     * @param dataScope 可信登录上下文解析出的商户数据范围
     * @return 当前原交易的保证金结算动作分页
     */
    PageResult<ReserveItemSummary> searchReserveItemsByTransaction(String transactionId,
                                                                   LocalDateTime transactionDateTime,
                                                                   Integer pageNo,
                                                                   Integer pageSize,
                                                                   AdminMerchantDataScope dataScope);

    /**
     * 分页查询正式结算批次对应的资金入账流水。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<PostingSummary> searchPostings(PostingSearchRequest request,
                                              AdminMerchantDataScope dataScope);
}
