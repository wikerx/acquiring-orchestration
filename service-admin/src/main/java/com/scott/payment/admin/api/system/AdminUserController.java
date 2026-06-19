package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminUserApplicationService;
import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountResetPasswordRequest;
import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
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
 * 管理后台用户接口入口。
 *
 * <p>Controller 仅接收参数、校验权限并调用
 * {@link AdminUserApplicationService}，用户、角色和密码规则均下沉到应用层及领域服务。</p>
 */
@RestController
@RequestMapping("/admin/system/users")
public class AdminUserController {

    /**
     * 后台用户应用服务。
     */
    private final AdminUserApplicationService adminUserApplicationService;

    /**
     * 创建后台用户控制器。
     *
     * @param adminUserApplicationService 后台用户应用服务
     */
    public AdminUserController(AdminUserApplicationService adminUserApplicationService) {
        this.adminUserApplicationService = adminUserApplicationService;
    }

    /**
     * 分页查询后台用户。
     *
     * @param request 查询条件
     * @return 用户分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("system:user:list")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询后台用户列表")
    public CommonResult<PageResult<SysUserAccountDTO>> listUsers(@RequestBody(required = false) SysUserAccountQueryRequest request) {
        return success(adminUserApplicationService.pageUsers(request));
    }

    /**
     * 新增后台用户。
     *
     * @param request 新增请求
     * @return 新增后的用户
     */
    @PostMapping("/create")
    @RequiresPermission("system:user:add")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.CREATE,
            operation = "新增后台用户", recordRequest = false, recordResponse = false)
    public CommonResult<SysUserAccountDTO> createUser(@Valid @RequestBody SysUserAccountCreateRequest request) {
        return success(adminUserApplicationService.createUser(request));
    }

    /**
     * 编辑后台用户。
     *
     * @param request 更新请求
     * @return 更新后的用户
     */
    @PostMapping("/update")
    @RequiresPermission("system:user:edit")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "编辑后台用户", recordRequest = false, recordResponse = false)
    public CommonResult<SysUserAccountDTO> updateUser(@Valid @RequestBody SysUserAccountUpdateRequest request) {
        return success(adminUserApplicationService.updateUser(request));
    }

    /**
     * 更新后台用户状态。
     *
     * @param request 状态更新请求
     * @return 空响应
     */
    @PostMapping("/status")
    @RequiresPermission("system:user:changeStatus")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "更新后台用户状态", recordRequest = false, recordResponse = false)
    public CommonResult<Void> updateStatus(@Valid @RequestBody SysUserAccountStatusRequest request) {
        adminUserApplicationService.updateStatus(request);
        return success();
    }

    /**
     * 重置后台用户密码。
     *
     * @param request 重置密码请求
     * @return 空响应
     */
    @PostMapping("/reset-password")
    @RequiresPermission("system:user:resetPwd")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重置后台用户密码", recordRequest = false, recordResponse = false)
    public CommonResult<Void> resetPassword(@Valid @RequestBody SysUserAccountResetPasswordRequest request) {
        adminUserApplicationService.resetPassword(request);
        return success();
    }

    /**
     * 查询后台用户角色授权。
     *
     * @param request 用户角色请求
     * @return 用户角色授权信息
     */
    @PostMapping("/roles")
    @RequiresPermission("system:user:assign-role")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.QUERY,
            operation = "查询后台用户角色授权")
    public CommonResult<SysUserRoleAuthDTO> userRoles(@Valid @RequestBody SysUserRoleGrantRequest request) {
        return success(adminUserApplicationService.userRoles(request.getAccountId()));
    }

    /**
     * 保存后台用户角色授权。
     *
     * @param request 角色授权请求
     * @return 空响应
     */
    @PostMapping("/roles/grant")
    @RequiresPermission("system:user:assign-role")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "分配后台用户角色", recordRequest = false, recordResponse = false)
    public CommonResult<Void> grantRoles(@Valid @RequestBody SysUserRoleGrantRequest request) {
        adminUserApplicationService.grantRoles(request);
        return success();
    }
}
