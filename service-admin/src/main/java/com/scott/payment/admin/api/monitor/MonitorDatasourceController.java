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
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorDatasourceController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Monitor Datasource 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
