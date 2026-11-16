package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.export.AdminRiskMonitorExportSourceApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminDatasourceExportInternalDTOs;
import com.scott.payment.admin.dto.exporttask.AdminRiskExportInternalDTOs;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/** service-data 读取风控配置和数据源快照的 HMAC 内部入口。 */
@RestController
@RequestMapping("/internal/admin")
public class AdminRiskMonitorExportInternalController {
    private final AdminRiskMonitorExportSourceApplicationService applicationService;

    /** 创建只读内部来源入口。 */
    public AdminRiskMonitorExportInternalController(
            AdminRiskMonitorExportSourceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 按冻结功能和筛选条件分页读取风控配置安全投影。 */
    @PostMapping("/risk/configs/export-page")
    public CommonResult<AdminRiskExportInternalDTOs.PageResponse> riskConfigPage(
            @Valid @RequestBody AdminRiskExportInternalDTOs.PageRequest request) {
        return success(applicationService.pageRiskConfig(request));
    }

    /** 读取当前 Admin 服务的脱敏数据源运行快照。 */
    @PostMapping("/monitor/datasource-snapshot/export-page")
    public CommonResult<AdminDatasourceExportInternalDTOs.PageResponse> datasourceSnapshotPage(
            @Valid @RequestBody AdminDatasourceExportInternalDTOs.PageRequest request) {
        return success(applicationService.pageDatasourceSnapshot(request));
    }
}
