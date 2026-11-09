package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.export.AdminSettlementExportSourceApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReservePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReservePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionPageResponse;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-data 调用的结算导出源内部入口，受内部 HMAC 和精确路径白名单保护。
 */
@RestController
@RequestMapping("/internal/admin/settlement")
public class AdminSettlementExportInternalController {
    private final AdminSettlementExportSourceApplicationService applicationService;

    /**
     * 创建仅供异步导出执行器使用的结算来源入口。
     */
    public AdminSettlementExportInternalController(
            AdminSettlementExportSourceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 按冻结范围分页读取结算交易明细安全投影。
     */
    @PostMapping("/result-items/export-page")
    public CommonResult<SettlementTransactionPageResponse> transactionPage(
            @Valid @RequestBody SettlementTransactionPageRequest request) {
        return success(applicationService.pageTransactions(request));
    }

    /**
     * 按冻结范围分页读取保证金结算明细安全投影。
     */
    @PostMapping("/reserve-items/export-page")
    public CommonResult<SettlementReservePageResponse> reservePage(
            @Valid @RequestBody SettlementReservePageRequest request) {
        return success(applicationService.pageReserves(request));
    }
}
