package com.scott.payment.admin.api.fee;

import com.scott.payment.admin.application.fee.AdminFeeApplicationService;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanDetailResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanSummaryResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeReviewRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeReviewResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeTemplateCreateRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeTemplateStatusRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantFeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantTemplateAssignRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeController
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端费用配置接口，只接收参数、校验权限并委托应用服务。
 * @status : create
 */
@RestController
@RequestMapping("/admin/fees")
public class AdminFeeController {

    private final AdminFeeApplicationService applicationService;

    /** 构造费用配置接口。 */
    public AdminFeeController(AdminFeeApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 分页查询费用模板。 */
    @PostMapping("/templates/search")
    @RequiresPermission("fee:template:list")
    public CommonResult<PageResult<FeePlanSummaryResponse>> pageTemplates(
            @RequestBody(required = false) FeePlanQuery query) {
        return success(applicationService.pageTemplates(query));
    }

    /** 按筛选条件导出费用模板。 */
    @PostMapping("/templates/export")
    @RequiresPermission("fee:template:export")
    @OperationLog(moduleName = "费用模板", businessType = OperationTypeConstants.EXPORT, operation = "导出费用模板")
    public void exportTemplates(@RequestBody(required = false) FeePlanQuery query, HttpServletResponse response) {
        applicationService.exportTemplates(query, response);
    }

    /** 查询模板详情和版本历史。 */
    @GetMapping("/templates/{id}")
    @RequiresPermission("fee:template:detail")
    public CommonResult<FeePlanDetailResponse> getTemplate(@PathVariable("id") Long id) {
        return success(applicationService.getTemplate(id));
    }

    /** 新建模板并提交审核。 */
    @PostMapping("/templates")
    @RequiresPermission("fee:template:add")
    @OperationLog(moduleName = "费用模板", businessType = OperationTypeConstants.CREATE, operation = "新建费用模板版本")
    public CommonResult<FeePlanDetailResponse> createTemplate(
            @Valid @RequestBody FeeTemplateCreateRequest request) {
        return success(applicationService.createTemplate(request));
    }

    /** 创建模板新版本并提交审核。 */
    @PostMapping("/templates/{id}/versions")
    @RequiresPermission("fee:template:edit")
    @OperationLog(moduleName = "费用模板", businessType = OperationTypeConstants.UPDATE, operation = "提交费用模板新版本")
    public CommonResult<FeePlanDetailResponse> createTemplateVersion(
            @PathVariable("id") Long id,
            @Valid @RequestBody FeeVersionSaveRequest request) {
        return success(applicationService.createTemplateVersion(id, request));
    }

    /** 启用或禁用模板，只影响后续商户选择。 */
    @PutMapping("/templates/{id}/status")
    @RequiresPermission("fee:template:status")
    @OperationLog(moduleName = "费用模板", businessType = OperationTypeConstants.UPDATE, operation = "更新费用模板状态")
    public CommonResult<Void> updateTemplateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody FeeTemplateStatusRequest request) {
        applicationService.updateTemplateStatus(id, request.getEnabled());
        return success(null);
    }

    /** 归档模板，不删除历史和已有商户副本。 */
    @PutMapping("/templates/{id}/archive")
    @RequiresPermission("fee:template:archive")
    @OperationLog(moduleName = "费用模板", businessType = OperationTypeConstants.UPDATE, operation = "归档费用模板")
    public CommonResult<Void> archiveTemplate(@PathVariable("id") Long id) {
        applicationService.archiveTemplate(id);
        return success(null);
    }

    /** 分页查询商户费用配置状态。 */
    @PostMapping("/merchants/search")
    @RequiresPermission("fee:merchant:list")
    public CommonResult<PageResult<FeePlanSummaryResponse>> pageMerchantFees(
            @RequestBody(required = false) FeePlanQuery query) {
        return success(applicationService.pageMerchantFees(query));
    }

    /** 按筛选条件导出商户费率。 */
    @PostMapping("/merchants/export")
    @RequiresPermission("fee:merchant:export")
    @OperationLog(moduleName = "商户费率", businessType = OperationTypeConstants.EXPORT, operation = "导出商户费率")
    public void exportMerchantFees(@RequestBody(required = false) FeePlanQuery query, HttpServletResponse response) {
        applicationService.exportMerchantFees(query, response);
    }

    /** 查询商户费用配置详情。 */
    @GetMapping("/merchants/{merchantId}")
    @RequiresPermission("fee:merchant:detail")
    public CommonResult<FeePlanDetailResponse> getMerchantFee(@PathVariable("merchantId") String merchantId) {
        return success(applicationService.getMerchantFee(merchantId));
    }

    /** 原样复制模板当前生效版本并提交审核。 */
    @PostMapping("/merchants/{merchantId}/template-versions")
    @RequiresPermission("fee:merchant:template:assign")
    @OperationLog(moduleName = "商户费率", businessType = OperationTypeConstants.UPDATE, operation = "商户选择费率模板")
    public CommonResult<FeePlanDetailResponse> assignMerchantTemplate(
            @PathVariable("merchantId") String merchantId,
            @Valid @RequestBody MerchantTemplateAssignRequest request) {
        return success(applicationService.assignMerchantTemplate(merchantId, request));
    }

    /** 提交独立配置或基于模板调整后的商户费率版本。 */
    @PostMapping("/merchants/{merchantId}/custom-versions")
    @RequiresPermission("fee:merchant:configure")
    @OperationLog(moduleName = "商户费率", businessType = OperationTypeConstants.UPDATE, operation = "提交商户自定义费率版本")
    public CommonResult<FeePlanDetailResponse> createMerchantCustomVersion(
            @PathVariable("merchantId") String merchantId,
            @Valid @RequestBody MerchantFeeVersionSaveRequest request) {
        return success(applicationService.createMerchantCustomVersion(merchantId, request));
    }

    /** 分页查询待审核费用版本。 */
    @PostMapping("/reviews/search")
    @RequiresPermission("fee:review:list")
    public CommonResult<PageResult<FeeReviewResponse>> pageReviews(
            @RequestBody(required = false) FeePlanQuery query) {
        return success(applicationService.pageReviews(query));
    }

    /** 按筛选条件导出费率复核记录。 */
    @PostMapping("/reviews/export")
    @RequiresPermission("fee:review:export")
    @OperationLog(moduleName = "费率审核", businessType = OperationTypeConstants.EXPORT, operation = "导出费率审核记录")
    public void exportReviews(@RequestBody(required = false) FeePlanQuery query, HttpServletResponse response) {
        applicationService.exportReviews(query, response);
    }

    /** 审核通过，保存成功时间即生效时间。 */
    @PutMapping("/versions/{id}/approve")
    @RequiresPermission("fee:review:approve")
    @OperationLog(moduleName = "费率审核", businessType = OperationTypeConstants.UPDATE, operation = "审核通过费用版本")
    public CommonResult<FeePlanDetailResponse> approveVersion(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) FeeReviewRequest request) {
        return success(applicationService.approveVersion(id, request == null ? null : request.getReviewComment()));
    }

    /** 审核拒绝并保留原因。 */
    @PutMapping("/versions/{id}/reject")
    @RequiresPermission("fee:review:reject")
    @OperationLog(moduleName = "费率审核", businessType = OperationTypeConstants.UPDATE, operation = "审核拒绝费用版本")
    public CommonResult<FeePlanDetailResponse> rejectVersion(
            @PathVariable("id") Long id,
            @Valid @RequestBody FeeReviewRequest request) {
        return success(applicationService.rejectVersion(id, request.getReviewComment()));
    }

    /** 使用系统当前有效的标签币种到 USD 正向结算汇率进行费用试算。 */
    @PostMapping("/simulations")
    @RequiresPermission("fee:simulation:use")
    @OperationLog(moduleName = "费用试算", businessType = OperationTypeConstants.QUERY, operation = "执行费用试算")
    public CommonResult<FeeSimulationResponse> simulate(@Valid @RequestBody FeeSimulationRequest request) {
        return success(applicationService.simulate(request));
    }

    /** 分页查询费用试算记录。 */
    @PostMapping("/simulation-records/search")
    @RequiresPermission("fee:simulation:record:list")
    public CommonResult<PageResult<FeeSimulationRecordResponse>> pageSimulationRecords(
            @RequestBody(required = false) FeeSimulationRecordQuery query) {
        return success(applicationService.pageSimulationRecords(query));
    }

    /** 按筛选条件导出费用试算记录。 */
    @PostMapping("/simulation-records/export")
    @RequiresPermission("fee:simulation:record:export")
    @OperationLog(moduleName = "费用试算", businessType = OperationTypeConstants.EXPORT, operation = "导出费用试算记录")
    public void exportSimulationRecords(
            @RequestBody(required = false) FeeSimulationRecordQuery query,
            HttpServletResponse response) {
        applicationService.exportSimulationRecords(query, response);
    }
}
