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

    /**
     * 强制指定后台用户进入 MFA 绑定流程；请求和响应不写操作日志正文。
     *
     * @param request 目标用户及操作原因
     * @return 更新后的 MFA 状态
     */
    @PostMapping("/require")
    @RequiresPermission("sys:user:mfa:require")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "强制启用用户 MFA", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> requireMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.requireMfa(request));
    }

    /**
     * 重置指定后台用户的 MFA 绑定，旧密钥立即失效。
     *
     * @param request 目标用户及操作原因
     * @return 重置后的 MFA 状态
     */
    @PostMapping("/reset")
    @RequiresPermission("sys:user:mfa:reset")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重置用户 MFA", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> resetMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.resetMfa(request));
    }

    /**
     * 配置后台用户 MFA 豁免及有效期。
     *
     * @param request 目标用户、豁免期限和原因
     * @return 更新后的 MFA 状态
     */
    @PostMapping("/exempt")
    @RequiresPermission("sys:user:mfa:exempt")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "配置用户 MFA 豁免", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> exemptMfa(@Valid @RequestBody UserMfaExemptRequest request) {
        return success(adminUserMfaApplicationService.exemptMfa(request));
    }

    /**
     * 停用指定后台用户 MFA，权限和状态约束由应用服务校验。
     *
     * @param request 目标用户及停用原因
     * @return 更新后的 MFA 状态
     */
    @PostMapping("/disable")
    @RequiresPermission("sys:user:mfa:disable")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "停用用户 MFA", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> disableMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.disableMfa(request));
    }

    /**
     * 解除指定后台用户因连续失败产生的 MFA 锁定。
     *
     * @param request 目标用户及操作原因
     * @return 解锁后的 MFA 状态
     */
    @PostMapping("/unlock")
    @RequiresPermission("sys:user:mfa:unlock")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "解锁用户 MFA", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> unlockMfa(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.unlockMfa(request));
    }

    /**
     * 重新发送 MFA 绑定邮件，不返回或记录绑定密钥。
     *
     * @param request 目标用户及操作原因
     * @return 当前 MFA 绑定状态
     */
    @PostMapping("/resend-bind-mail")
    @RequiresPermission("sys:user:mfa:resend")
    @OperationLog(moduleName = "用户 MFA 管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重发 MFA 绑定邮件", recordRequest = false, recordResponse = false)
    public CommonResult<UserMfaStatusResponse> resendBindMail(@Valid @RequestBody UserMfaActionRequest request) {
        return success(adminUserMfaApplicationService.resendBindMail(request));
    }

    /**
     * 分页查询后台用户 MFA 管理审计日志。
     *
     * @param query 用户、动作和时间范围等可选条件
     * @return MFA 审计日志分页结果
     */
    @PostMapping("/logs/search")
    @RequiresPermission("sys:user:mfa:log")
    public CommonResult<PageResult<UserMfaLogResponse>> pageLogs(@RequestBody(required = false) UserMfaLogQuery query) {
        return success(adminUserMfaApplicationService.pageLogs(query));
    }
}
