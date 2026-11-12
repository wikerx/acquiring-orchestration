package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.export.AdminFundExportSourceApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundAccountPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundAccountPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundDeductionPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundDeductionPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundLedgerPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundLedgerPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundRechargePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundRechargePageResponse;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-data 调用的管理端资金账户域导出源入口，受内部 HMAC 和精确路径白名单保护。
 */
@RestController
@RequestMapping("/internal/admin/fund")
public class AdminFundExportInternalController {
    private final AdminFundExportSourceApplicationService applicationService;

    /**
     * 创建仅供异步导出执行器使用的资金来源入口。
     *
     * @param applicationService 资金账户域导出来源服务
     */
    public AdminFundExportInternalController(AdminFundExportSourceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 按冻结范围分页读取资金账户安全投影。 */
    @PostMapping("/accounts/export-page")
    public CommonResult<FundAccountPageResponse> accountPage(
            @Valid @RequestBody FundAccountPageRequest request) {
        return success(applicationService.pageAccounts(request));
    }

    /** 按冻结范围分页读取指定账户或全局余额流水安全投影。 */
    @PostMapping("/ledgers/export-page")
    public CommonResult<FundLedgerPageResponse> ledgerPage(
            @Valid @RequestBody FundLedgerPageRequest request) {
        return success(applicationService.pageLedgers(request));
    }

    /** 按冻结范围分页读取充值申请安全投影。 */
    @PostMapping("/recharges/export-page")
    public CommonResult<FundRechargePageResponse> rechargePage(
            @Valid @RequestBody FundRechargePageRequest request) {
        return success(applicationService.pageRecharges(request));
    }

    /** 按冻结范围分页读取扣减申请安全投影。 */
    @PostMapping("/deductions/export-page")
    public CommonResult<FundDeductionPageResponse> deductionPage(
            @Valid @RequestBody FundDeductionPageRequest request) {
        return success(applicationService.pageDeductions(request));
    }
}
