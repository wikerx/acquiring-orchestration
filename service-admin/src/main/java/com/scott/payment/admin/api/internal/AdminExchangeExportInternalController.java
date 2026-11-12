package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.export.AdminExchangeExportSourceApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.BusinessRatePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.BusinessRatePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RawRatePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RawRatePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.UsageSnapshotPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.UsageSnapshotPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.SourcePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.SourcePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RulePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RulePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.VolatilityPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.VolatilityPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertRulePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertRulePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertEventPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertEventPageResponse;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * service-data 调用的管理端汇率事实导出源入口，受 HMAC 和精确路径白名单保护。
 */
@RestController
@RequestMapping("/internal/admin/exchange")
public class AdminExchangeExportInternalController {
    private final AdminExchangeExportSourceApplicationService applicationService;

    /**
     * 创建仅供异步导出执行器使用的汇率事实来源入口。
     *
     * @param applicationService 汇率事实导出来源服务
     */
    public AdminExchangeExportInternalController(AdminExchangeExportSourceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 按冻结条件分页读取原始汇率事实。 */
    @PostMapping("/raw-rates/export-page")
    public CommonResult<RawRatePageResponse> rawRatePage(
            @Valid @RequestBody RawRatePageRequest request) {
        return success(applicationService.pageRawRates(request));
    }

    /** 按冻结条件分页读取业务汇率事实。 */
    @PostMapping("/business-rates/export-page")
    public CommonResult<BusinessRatePageResponse> businessRatePage(
            @Valid @RequestBody BusinessRatePageRequest request) {
        return success(applicationService.pageBusinessRates(request));
    }

    /** 按冻结条件分页读取汇率使用快照事实。 */
    @PostMapping("/usage-snapshots/export-page")
    public CommonResult<UsageSnapshotPageResponse> usageSnapshotPage(
            @Valid @RequestBody UsageSnapshotPageRequest request) {
        return success(applicationService.pageUsageSnapshots(request));
    }

    /** 按冻结条件分页读取汇率源配置。 */
    @PostMapping("/sources/export-page")
    public CommonResult<SourcePageResponse> sourcePage(
            @Valid @RequestBody SourcePageRequest request) {
        return success(applicationService.pageSources(request));
    }

    /** 按冻结条件分页读取汇率规则配置。 */
    @PostMapping("/rules/export-page")
    public CommonResult<RulePageResponse> rulePage(
            @Valid @RequestBody RulePageRequest request) {
        return success(applicationService.pageRules(request));
    }

    /** 按冻结条件分页读取权威汇率波动摘要。 */
    @PostMapping("/volatility/export-page")
    public CommonResult<VolatilityPageResponse> volatilityPage(
            @Valid @RequestBody VolatilityPageRequest request) {
        return success(applicationService.pageVolatility(request));
    }

    /** 按冻结条件分页读取汇率预警规则。 */
    @PostMapping("/alert-rules/export-page")
    public CommonResult<AlertRulePageResponse> alertRulePage(
            @Valid @RequestBody AlertRulePageRequest request) {
        return success(applicationService.pageAlertRules(request));
    }

    /** 按冻结条件分页读取汇率预警事件只读事实。 */
    @PostMapping("/alert-events/export-page")
    public CommonResult<AlertEventPageResponse> alertEventPage(
            @Valid @RequestBody AlertEventPageRequest request) {
        return success(applicationService.pageAlertEvents(request));
    }
}
