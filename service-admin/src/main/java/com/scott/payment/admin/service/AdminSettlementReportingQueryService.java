package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
import com.scott.payment.component.core.model.PageResult;

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
    PageResult<ResultItemSummary> searchResultItems(ResultItemSearchRequest request,
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
