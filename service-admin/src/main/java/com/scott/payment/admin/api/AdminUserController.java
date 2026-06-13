package com.scott.payment.admin.api;

import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountResetPasswordRequest;
import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import com.scott.payment.admin.service.AdminUserService;
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
 * @classname : AdminUserController
 * @date : 2026-06-06 00:00
 * @description : 管理后台用户内部接口
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/users")
public class AdminUserController {

    private final AdminUserService userService;

    public AdminUserController(AdminUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/search")
    @RequiresPermission("system:user:list")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询后台用户列表")
    public CommonResult<PageResult<SysUserAccountDTO>> listUsers(@RequestBody(required = false) SysUserAccountQueryRequest request) {
        return success(userService.pageUsers(request));
    }

    @PostMapping("/create")
    @RequiresPermission("system:user:add")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.CREATE,
            operation = "新增后台用户", recordRequest = false, recordResponse = false)
    public CommonResult<SysUserAccountDTO> createUser(@Valid @RequestBody SysUserAccountCreateRequest request) {
        return success(userService.createUser(request));
    }

    @PostMapping("/update")
    @RequiresPermission("system:user:edit")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "编辑后台用户", recordRequest = false, recordResponse = false)
    public CommonResult<SysUserAccountDTO> updateUser(@Valid @RequestBody SysUserAccountUpdateRequest request) {
        return success(userService.updateUser(request));
    }

    @PostMapping("/status")
    @RequiresPermission("system:user:changeStatus")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "更新后台用户状态", recordRequest = false, recordResponse = false)
    public CommonResult<Void> updateStatus(@Valid @RequestBody SysUserAccountStatusRequest request) {
        userService.updateStatus(request);
        return success();
    }

    @PostMapping("/reset-password")
    @RequiresPermission("system:user:resetPwd")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "重置后台用户密码", recordRequest = false, recordResponse = false)
    public CommonResult<Void> resetPassword(@Valid @RequestBody SysUserAccountResetPasswordRequest request) {
        userService.resetPassword(request);
        return success();
    }

    @PostMapping("/roles")
    @RequiresPermission("system:user:assign-role")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.QUERY,
            operation = "查询后台用户角色授权")
    public CommonResult<SysUserRoleAuthDTO> userRoles(@Valid @RequestBody SysUserRoleGrantRequest request) {
        return success(userService.userRoles(request.getAccountId()));
    }

    @PostMapping("/roles/grant")
    @RequiresPermission("system:user:assign-role")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.UPDATE,
            operation = "分配后台用户角色", recordRequest = false, recordResponse = false)
    public CommonResult<Void> grantRoles(@Valid @RequestBody SysUserRoleGrantRequest request) {
        userService.grantRoles(request);
        return success();
    }
}
