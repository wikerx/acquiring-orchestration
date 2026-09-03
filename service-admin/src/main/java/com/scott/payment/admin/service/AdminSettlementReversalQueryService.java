package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSummary;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReversalQueryService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 冲正单本地只读查询边界。
 * @status : create
 */
public interface AdminSettlementReversalQueryService {

    /**
     * 查询{@code search}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<ReversalSummary> search(ReversalSearchRequest request, AdminMerchantDataScope dataScope);

    /**
     * 查询指定业务单据详情，并执行调用方数据范围校验。
     * @param reversalOrderNo 结算冲正申请单号
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 冲正申请详情
     */
    ReversalDetailResponse detail(String reversalOrderNo, AdminMerchantDataScope dataScope);

    /**
     * 校验当前操作人是否有权访问指定结算冲正申请。
     * <p>
     * 校验失败时按 运营后台服务 统一异常语义中断流程，不返回部分校验结果。
     * </p>
     * @param reversalOrderNo 结算冲正申请单号
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     */
    void requireAccess(String reversalOrderNo, AdminMerchantDataScope dataScope);
}
