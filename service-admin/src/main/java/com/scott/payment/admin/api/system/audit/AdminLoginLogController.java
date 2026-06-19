package com.scott.payment.admin.api.system.audit;

import com.scott.payment.admin.application.system.AdminLoginLogApplicationService;
import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.admin.dto.SysLoginLogQueryRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台登录日志接口入口。
 *
 * <p>仅负责接收查询条件并调用
 * {@link AdminLoginLogApplicationService} 返回分页结果。</p>
 */
@RestController
@RequestMapping("/admin/system/login-logs")
public class AdminLoginLogController {

    /**
     * 登录日志应用服务。
     */
    private final AdminLoginLogApplicationService adminLoginLogApplicationService;

    /**
     * 创建登录日志控制器。
     *
     * @param adminLoginLogApplicationService 登录日志应用服务
     */
    public AdminLoginLogController(AdminLoginLogApplicationService adminLoginLogApplicationService) {
        this.adminLoginLogApplicationService = adminLoginLogApplicationService;
    }

    /**
     * 按条件查询登录日志。
     *
     * @param request 查询条件
     * @return 登录日志分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("system:login-log:list")
    @OperationLog(moduleName = "登录日志", businessType = OperationTypeConstants.QUERY, operation = "分页查询后台登录日志列表")
    public CommonResult<PageResult<SysLoginLogDTO>> listLoginLogs(@RequestBody(required = false) SysLoginLogQueryRequest request) {
        return success(adminLoginLogApplicationService.pageLoginLogs(request));
    }
}
