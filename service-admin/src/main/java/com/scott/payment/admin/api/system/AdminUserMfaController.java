package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminUserMfaApplicationService;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaActionRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaExemptRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogQuery;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogResponse;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaStatusResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserMfaController
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 后台用户 MFA 管理接口，位于 service-admin 接口层；负责多因素认证管理操作的 HTTP 映射、权限校验和参数接收。
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/users/mfa")
public class AdminUserMfaController {

    private final AdminUserMfaApplicationService adminUserMfaApplicationService;

    /**
     * 创建后台用户 MFA 控制器。
     *
     * @param adminUserMfaApplicationService MFA 应用服务
     */
    public AdminUserMfaController(AdminUserMfaApplicationService adminUserMfaApplicationService) {
        this.adminUserMfaApplicationService = adminUserMfaApplicationService;
    }

    @PostMapping("/require")
    @RequiresPermission("sys:user:mfa:require")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "强制启用用户 MFA", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> requireMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.requireMfa(request));
    }

    @PostMapping("/reset")
    @RequiresPermission("sys:user:mfa:reset")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重置用户 MFA", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> resetMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.resetMfa(request));
    }

    @PostMapping("/exempt")
    @RequiresPermission("sys:user:mfa:exempt")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "配置用户 MFA 豁免", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> exemptMfa(@Valid @RequestBody UserMfaExemptRequest request) {
        return success(adminUserMfaApplicationService.exemptMfa(request));
    }

    @PostMapping("/disable")
    @RequiresPermission("sys:user:mfa:disable")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "停用用户 MFA", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> disableMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.disableMfa(request));
    }

    @PostMapping("/unlock")
    @RequiresPermission("sys:user:mfa:unlock")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "解锁用户 MFA", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> unlockMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.unlockMfa(request));
    }

    @PostMapping("/resend-bind-mail")
    @RequiresPermission("sys:user:mfa:resend")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重发 MFA 绑定邮件", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> resendBindMail(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.resendBindMail(request));
    }

    @PostMapping("/logs/search")
    @RequiresPermission("sys:user:mfa:log")
    public CommonResult<PageResult<UserMfaLogResponse>> pageLogs(@RequestBody(required = false) UserMfaLogQuery query) {
        return success(adminUserMfaApplicationService.pageLogs(query));
    }
}
