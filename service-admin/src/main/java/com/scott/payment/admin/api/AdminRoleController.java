package com.scott.payment.admin.api;

import com.scott.payment.admin.dto.SysRoleCreateRequest;
import com.scott.payment.admin.dto.SysRoleDeleteRequest;
import com.scott.payment.admin.dto.SysRoleDTO;
import com.scott.payment.admin.dto.SysRoleMenuAuthDTO;
import com.scott.payment.admin.dto.SysRoleMenuGrantRequest;
import com.scott.payment.admin.dto.SysRolePermissionAuthDTO;
import com.scott.payment.admin.dto.SysRolePermissionGrantRequest;
import com.scott.payment.admin.dto.SysRoleQueryRequest;
import com.scott.payment.admin.dto.SysRoleStatusRequest;
import com.scott.payment.admin.dto.SysRoleUpdateRequest;
import com.scott.payment.admin.service.AdminRoleService;
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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleController
 * @date : 2026-06-07 00:00
 * @description : 管理后台角色内部接口
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/roles")
public class AdminRoleController {

    private final AdminRoleService roleService;

    public AdminRoleController(AdminRoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("/search")
    @RequiresPermission("system:role:list")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询后台角色列表")
    public CommonResult<PageResult<SysRoleDTO>> listRoles(@RequestBody(required = false) SysRoleQueryRequest request) {
        return CommonResult.success(roleService.pageRoles(request));
    }

    @PostMapping("/create")
    @RequiresPermission("system:role:add")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.CREATE,
            operation = "新增后台角色", recordRequest = false, recordResponse = false)
    public CommonResult<SysRoleDTO> createRole(@Valid @RequestBody SysRoleCreateRequest request) {
        return CommonResult.success(roleService.createRole(request));
    }

    @PostMapping("/update")
    @RequiresPermission("system:role:edit")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.UPDATE,
            operation = "编辑后台角色", recordRequest = false, recordResponse = false)
    public CommonResult<SysRoleDTO> updateRole(@Valid @RequestBody SysRoleUpdateRequest request) {
        return CommonResult.success(roleService.updateRole(request));
    }

    @PostMapping("/status")
    @RequiresPermission("system:role:edit")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.UPDATE,
            operation = "更新后台角色状态", recordRequest = false, recordResponse = false)
    public CommonResult<Void> updateStatus(@Valid @RequestBody SysRoleStatusRequest request) {
        roleService.updateStatus(request);
        return CommonResult.success();
    }

    @PostMapping("/delete")
    @RequiresPermission("system:role:delete")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.DELETE,
            operation = "删除后台角色", recordRequest = false, recordResponse = false)
    public CommonResult<Void> deleteRole(@Valid @RequestBody SysRoleDeleteRequest request) {
        roleService.deleteRole(request.getRoleId());
        return CommonResult.success();
    }

    @PostMapping("/menus")
    @RequiresPermission("system:role:assign-menu")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.QUERY,
            operation = "查询角色菜单授权")
    public CommonResult<SysRoleMenuAuthDTO> roleMenus(@Valid @RequestBody SysRoleDeleteRequest request) {
        return CommonResult.success(roleService.roleMenus(request.getRoleId()));
    }

    @PostMapping("/menus/grant")
    @RequiresPermission("system:role:assign-menu")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.UPDATE,
            operation = "保存角色菜单授权", recordRequest = false, recordResponse = false)
    public CommonResult<Void> grantMenus(@Valid @RequestBody SysRoleMenuGrantRequest request) {
        roleService.grantMenus(request);
        return CommonResult.success();
    }

    @PostMapping("/permissions")
    @RequiresPermission("system:role:assign-permission")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.QUERY,
            operation = "查询角色权限授权")
    public CommonResult<SysRolePermissionAuthDTO> rolePermissions(@Valid @RequestBody SysRoleDeleteRequest request) {
        return CommonResult.success(roleService.rolePermissions(request.getRoleId()));
    }

    @PostMapping("/permissions/grant")
    @RequiresPermission("system:role:assign-permission")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.UPDATE,
            operation = "保存角色权限授权", recordRequest = false, recordResponse = false)
    public CommonResult<Void> grantPermissions(@Valid @RequestBody SysRolePermissionGrantRequest request) {
        roleService.grantPermissions(request);
        return CommonResult.success();
    }
}
