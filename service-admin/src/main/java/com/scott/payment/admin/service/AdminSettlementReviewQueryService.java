package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.CandidateSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReviewQueryService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 结算候选和预审单本地只读查询边界。
 * @status : create
 */
public interface AdminSettlementReviewQueryService {

    /**
     * 分页查询当前操作人数据范围内的结算候选。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param sourceTypes 允许查询的结算候选来源类型集合
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<CandidateSummary> searchCandidates(CandidateSearchRequest request,
                                                  Set<String> sourceTypes,
                                                  AdminMerchantDataScope dataScope);

    /**
     * 查询指定结算候选详情，并校验来源类型和商户数据范围。
     * @param candidateNo 结算候选编号，用于定位唯一候选记录
     * @param sourceTypes 允许查询的结算候选来源类型集合
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 结算候选详情
     */
    CandidateSummary candidateDetail(String candidateNo,
                                     Set<String> sourceTypes,
                                     AdminMerchantDataScope dataScope);

    /**
     * 分页查询当前操作人数据范围内的结算预审单。
     * <p>
     * 只读操作；实现必须沿用 运营后台服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<ReviewSummary> searchReviews(ReviewSearchRequest request,
                                            AdminMerchantDataScope dataScope);

    /**
     * 查询指定结算预审单详情，并校验商户数据范围。
     * @param reviewOrderNo 结算预审单号
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     * @return 结算预审单详情
     */
    ReviewDetailResponse reviewDetail(String reviewOrderNo, AdminMerchantDataScope dataScope);

    /**
     * 校验当前操作人是否有权访问指定结算候选。
     * <p>
     * 校验失败时按 运营后台服务 统一异常语义中断流程，不返回部分校验结果。
     * </p>
     * @param candidateIds 去重后的结算候选主键集合，最多 1000 个
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     */
    void requireCandidateAccess(List<Long> candidateIds, AdminMerchantDataScope dataScope);

    /**
     * 校验当前操作人是否有权访问指定结算预审单。
     * <p>
     * 校验失败时按 运营后台服务 统一异常语义中断流程，不返回部分校验结果。
     * </p>
     * @param reviewOrderNo 结算预审单号
     * @param dataScope 可信登录上下文解析出的商户数据范围，查询不得越过该范围
     */
    void requireReviewAccess(String reviewOrderNo, AdminMerchantDataScope dataScope);
}
