package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.export.AdminFeeExportSourceApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeePlanPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeReviewPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeSimulationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeSimulationPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeTemplatePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.MerchantFeePageResponse;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-data 调用的费用域导出源内部入口，受 HMAC 和精确路径白名单保护。
 */
@RestController
@RequestMapping("/internal/admin/fees")
public class AdminFeeExportInternalController {
    private final AdminFeeExportSourceApplicationService applicationService;

    /** 创建费用域内部导出源入口。 */
    public AdminFeeExportInternalController(AdminFeeExportSourceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 按冻结条件分页读取费用模板安全投影。 */
    @PostMapping("/templates/export-page")
    public CommonResult<FeeTemplatePageResponse> templatePage(
            @Valid @RequestBody FeePlanPageRequest request) {
        return success(applicationService.pageTemplates(request));
    }

    /** 按冻结条件和商户范围分页读取商户费率安全投影。 */
    @PostMapping("/merchants/export-page")
    public CommonResult<MerchantFeePageResponse> merchantFeePage(
            @Valid @RequestBody FeePlanPageRequest request) {
        return success(applicationService.pageMerchantFees(request));
    }

    /** 按冻结条件和商户范围分页读取费率复核安全投影。 */
    @PostMapping("/reviews/export-page")
    public CommonResult<FeeReviewPageResponse> reviewPage(
            @Valid @RequestBody FeePlanPageRequest request) {
        return success(applicationService.pageReviews(request));
    }

    /** 按冻结条件、商户范围和语言分页读取费用试算逐项投影。 */
    @PostMapping("/simulation-records/export-page")
    public CommonResult<FeeSimulationPageResponse> simulationPage(
            @Valid @RequestBody FeeSimulationPageRequest request) {
        return success(applicationService.pageSimulations(request));
    }
}
