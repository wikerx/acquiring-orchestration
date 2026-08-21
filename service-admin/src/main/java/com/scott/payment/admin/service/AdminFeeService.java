package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanDetailResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanSummaryResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeReviewResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeTemplateCreateRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantFeeVersionSaveRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端费用配置领域服务契约，负责版本、复核、模板复制和试算规则。
 * @status : create
 */
public interface AdminFeeService {

    /** 分页查询费用模板。 */
    PageResult<FeePlanSummaryResponse> pageTemplates(FeePlanQuery query);

    /** 查询费用模板详情和版本历史。 */
    FeePlanDetailResponse getTemplate(Long id);

    /** 新建费用模板并保存可编辑的 v1 草稿。 */
    FeePlanDetailResponse createTemplate(FeeTemplateCreateRequest request, Long operatorId, String operatorName);

    /** 基于当前配置创建新的模板草稿版本。 */
    FeePlanDetailResponse createTemplateVersion(Long id, FeeVersionSaveRequest request,
                                                Long operatorId, String operatorName);

    /** 原地更新尚未提交审核的模板草稿，已提交或已生效版本不可修改。 */
    FeePlanDetailResponse updateTemplateDraft(Long planId, Long versionId, FeeVersionSaveRequest request,
                                              Long operatorId, String operatorName);

    /** 将模板草稿提交审核，提交后配置保持不可变。 */
    FeePlanDetailResponse submitTemplateVersion(Long versionId, Long operatorId, String operatorName);

    /** 由原提交人撤回待审核模板并恢复为草稿。 */
    FeePlanDetailResponse withdrawTemplateVersion(Long versionId, Long operatorId, String operatorName);

    /** 启用或禁用模板，只控制后续商户是否可以选择。 */
    void updateTemplateStatus(Long id, boolean enabled, String operatorName);

    /** 归档模板，已有商户副本不受影响。 */
    void archiveTemplate(Long id, String operatorName);

    /** 分页查询全部商户及其当前费用配置状态。 */
    PageResult<FeePlanSummaryResponse> pageMerchantFees(FeePlanQuery query);

    /** 按商户号查询费用配置和版本历史。 */
    FeePlanDetailResponse getMerchantFee(String merchantId);

    /** 给商户复制模板、基于模板调整或创建独立配置。 */
    FeePlanDetailResponse createMerchantVersion(String merchantId, MerchantFeeVersionSaveRequest request,
                                                Long operatorId, String operatorName);

    /** 分页查询待复核版本。 */
    PageResult<FeeReviewResponse> pageReviews(FeePlanQuery query);

    /** 审核通过并在当前系统时间生效。 */
    FeePlanDetailResponse approveVersion(Long versionId, String comment, Long reviewerId, String reviewerName);

    /** 审核拒绝，保留版本和审核记录。 */
    FeePlanDetailResponse rejectVersion(Long versionId, String comment, Long reviewerId, String reviewerName);

    /** 按指定版本和系统当前有效正向结算汇率执行无资金副作用试算。 */
    FeeSimulationResponse simulate(FeeSimulationRequest request, Long operatorId, String operatorName);

    /** 分页查询已持久化的费用试算审计记录。 */
    PageResult<FeeSimulationRecordResponse> pageSimulationRecords(FeeSimulationRecordQuery query);
}
