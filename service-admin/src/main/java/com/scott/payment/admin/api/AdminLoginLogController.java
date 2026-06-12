package com.scott.payment.admin.api;

import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.admin.dto.SysLoginLogQueryRequest;
import com.scott.payment.admin.service.AdminLoginLogService;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminLoginLogController
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台登录日志内部接口
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/login-logs")
public class AdminLoginLogController {

    /**
     * 登录日志服务。
     */
    private final AdminLoginLogService loginLogService;

    /**
     * 创建登录日志内部接口。
     *
     * @param loginLogService 登录日志服务
     */
    public AdminLoginLogController(AdminLoginLogService loginLogService) {
        this.loginLogService = loginLogService;
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
        return success(loginLogService.pageLoginLogs(request));
    }
}
