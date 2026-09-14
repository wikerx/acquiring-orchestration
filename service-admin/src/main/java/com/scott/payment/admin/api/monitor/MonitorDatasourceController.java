package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminMonitorDatasourceApplicationService;
import com.scott.payment.admin.dto.monitor.DataSourceMonitorResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.DataSourceMetricsResponse;
import com.scott.payment.admin.service.monitor.AdminDatasourceMonitorSampler;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorDatasourceController
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 管理端数据源监控 HTTP 入口，负责权限、导出协议和统一响应，运行快照与指标采集分别委托应用服务和采样器。
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor/datasource")
public class MonitorDatasourceController {

    /** 数据源运行快照和导出应用服务。 */
    private final AdminMonitorDatasourceApplicationService adminMonitorDatasourceApplicationService;

    /** Hikari 连接池和 MySQL performance_schema 指标采样器。 */
    private final AdminDatasourceMonitorSampler datasourceMonitorSampler;

    /**
     * 创建数据源监控控制器。
     *
     * @param adminMonitorDatasourceApplicationService 数据源监控应用服务
     * @param datasourceMonitorSampler 数据源历史指标采样器
     */
    public MonitorDatasourceController(
            AdminMonitorDatasourceApplicationService adminMonitorDatasourceApplicationService,
            AdminDatasourceMonitorSampler datasourceMonitorSampler) {
        this.adminMonitorDatasourceApplicationService = adminMonitorDatasourceApplicationService;
        this.datasourceMonitorSampler = datasourceMonitorSampler;
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
     * 查询物理连接池趋势、SQL 延迟分位数和慢 SQL 指纹。
     *
     * @return 当前 Admin 进程内的数据源历史指标和能力状态
     */
    @GetMapping("/metrics")
    @RequiresPermission("monitor:datasource:view")
    public CommonResult<DataSourceMetricsResponse> metrics() {
        return success(datasourceMonitorSampler.metrics());
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
