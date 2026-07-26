package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskDashboardController
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 收单风控工作台接口，位于 service-admin 接口层，仅聚合管理端统计和配置变更日志。
 * @status : create
 */
@RestController
@RequestMapping("/admin/risk")
public class AdminRiskDashboardController {

    /**
     * risk Management Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminRiskManagementApplicationService riskManagementApplicationService;

    /**
     * 创建风控工作台接口。
     *
     * @param riskManagementApplicationService 风控管理应用服务
     */
    public AdminRiskDashboardController(AdminRiskManagementApplicationService riskManagementApplicationService) {
        this.riskManagementApplicationService = riskManagementApplicationService;
    }

    /**
     * 查询风险工作台概览。
     *
     * @return 风控功能启用数量和最近配置变更
     */
    @GetMapping("/dashboard")
    @RequiresPermission("risk:dashboard:overview:list")
    @OperationLog(moduleName = "收单风控-风险工作台", businessType = OperationTypeConstants.QUERY, operation = "查询风险工作台")
    public CommonResult<Map<String, Object>> dashboard() {
        return success(riskManagementApplicationService.dashboard());
    }

    /**
     * 查询今日风险事件。
     *
     * @return 当日风控评估记录
     */
    @GetMapping("/dashboard/today-events")
    @RequiresPermission("risk:dashboard:todayEvents:list")
    public CommonResult<java.util.List<Map<String, Object>>> todayEvents() {
        return success(riskManagementApplicationService.todayRiskEvents());
    }

    /**
     * 查询高风险商户排行。
     *
     * @return 近 30 天高风险商户统计
     */
    @GetMapping("/dashboard/merchant-ranking")
    @RequiresPermission("risk:dashboard:merchantRanking:list")
    public CommonResult<java.util.List<Map<String, Object>>> merchantRanking() {
        return success(riskManagementApplicationService.merchantRiskRanking());
    }

    /**
     * 分页查询风控配置变更日志。
     *
     * @param request 分页请求
     * @return 配置变更日志分页结果
     */
    @PostMapping("/changes/page")
    @RequiresPermission("risk:dashboard:configChanges:list")
    public CommonResult<PageResult<Map<String, Object>>> pageChangeLogs(@RequestBody(required = false) AdminRiskManagementApplicationService.PageRequestAdapter request) {
        return success(riskManagementApplicationService.pageChangeLogs(request));
    }
}
