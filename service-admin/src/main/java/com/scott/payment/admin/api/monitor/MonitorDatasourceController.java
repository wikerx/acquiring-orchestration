package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminMonitorDatasourceApplicationService;
import com.scott.payment.admin.dto.monitor.DataSourceMonitorResponse;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台数据源监控入口层。
 *
 * <p>该控制器只负责暴露“系统监控 / 数据源监控”页面需要的查询接口，
 * 具体的数据源快照聚合、分组分析和分表规则整理由应用服务层完成。</p>
 */
@RestController
@RequestMapping("/admin/monitor/datasource")
public class MonitorDatasourceController {

    /**
     * 数据源监控应用服务。
     */
    private final AdminMonitorDatasourceApplicationService adminMonitorDatasourceApplicationService;

    /**
     * 创建数据源监控控制器。
     *
     * @param adminMonitorDatasourceApplicationService 数据源监控应用服务
     */
    public MonitorDatasourceController(
            AdminMonitorDatasourceApplicationService adminMonitorDatasourceApplicationService) {
        this.adminMonitorDatasourceApplicationService = adminMonitorDatasourceApplicationService;
    }

    /**
     * 查询当前服务的数据源监控快照。
     *
     * <p>返回内容覆盖动态数据源注册情况、连接池运行状态、读写分组关系
     * 以及当前分表配置的物理落点，供管理后台直接渲染监控页面。</p>
     *
     * @return 数据源监控快照
     */
    @GetMapping
    @RequiresPermission("monitor:datasource:view")
    public CommonResult<DataSourceMonitorResponse> snapshot() {
        return success(adminMonitorDatasourceApplicationService.snapshot());
    }

    /**
     * 导出当前服务的数据源监控快照。
     *
     * <p>该导出仅包含运行时数据源、连接池和分表配置快照，不读取业务分表数据。</p>
     *
     * @param response HTTP 响应
     */
    @GetMapping("/export")
    @RequiresPermission("monitor:datasource:export")
    @OperationLog(moduleName = "数据源监控", businessType = OperationTypeConstants.EXPORT, operation = "导出数据源监控")
    public void export(HttpServletResponse response) {
        adminMonitorDatasourceApplicationService.exportSnapshot(currentOperatorName(), response);
    }

    /**
     * 获取当前操作人名称，用于写入导出元信息。
     *
     * @return 操作人名称
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount() == null ? "admin" : account.getLoginAccount();
    }
}
