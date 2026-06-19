package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminRoleApplicationService;
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

import static com.scott.payment.component.core.model.CommonResult.success;

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

    private final AdminRoleApplicationService adminRoleApplicationService;

    /**
     * 创建角色管理控制器。
     *
     * @param adminRoleApplicationService 角色应用服务
     */
    public AdminRoleController(AdminRoleApplicationService adminRoleApplicationService) {
        this.adminRoleApplicationService = adminRoleApplicationService;
    }

    /**
     * 分页查询后台角色。
     *
     * @param request 查询条件
     * @return 角色分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("system:role:list")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询后台角色列表")
    public CommonResult<PageResult<SysRoleDTO>> listRoles(@RequestBody(required = false) SysRoleQueryRequest request) {
        return success(adminRoleApplicationService.pageRoles(request));
    }

    /**
     * 新增后台角色。
     *
     * @param request 新增请求
     * @return 新增后的角色
     */
    @PostMapping("/create")
    @RequiresPermission("system:role:add")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.CREATE,
            operation = "新增后台角色", recordRequest = false, recordResponse = false)
    public CommonResult<SysRoleDTO> createRole(@Valid @RequestBody SysRoleCreateRequest request) {
        return success(adminRoleApplicationService.createRole(request));
    }

    /**
     * 编辑后台角色。
     *
     * @param request 更新请求
     * @return 更新后的角色
     */
    @PostMapping("/update")
    @RequiresPermission("system:role:edit")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.UPDATE,
            operation = "编辑后台角色", recordRequest = false, recordResponse = false)
    public CommonResult<SysRoleDTO> updateRole(@Valid @RequestBody SysRoleUpdateRequest request) {
        return success(adminRoleApplicationService.updateRole(request));
    }

    /**
     * 更新后台角色状态。
     *
     * @param request 状态更新请求
     * @return 空响应
     */
    @PostMapping("/status")
    @RequiresPermission("system:role:changeStatus")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.UPDATE,
            operation = "更新后台角色状态", recordRequest = false, recordResponse = false)
    public CommonResult<Void> updateStatus(@Valid @RequestBody SysRoleStatusRequest request) {
        adminRoleApplicationService.updateStatus(request);
        return success();
    }

    /**
     * 删除后台角色。
     *
     * @param request 删除请求
     * @return 空响应
     */
    @PostMapping("/delete")
    @RequiresPermission("system:role:remove")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.DELETE,
            operation = "删除后台角色", recordRequest = false, recordResponse = false)
    public CommonResult<Void> deleteRole(@Valid @RequestBody SysRoleDeleteRequest request) {
        adminRoleApplicationService.deleteRole(request.getRoleId());
        return success();
    }

    /**
     * 查询角色菜单授权。
     *
     * @param request 角色标识请求
     * @return 角色菜单授权信息
     */
    @PostMapping("/menus")
    @RequiresPermission("system:role:dataScope")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.QUERY,
            operation = "查询角色菜单授权")
    public CommonResult<SysRoleMenuAuthDTO> roleMenus(@Valid @RequestBody SysRoleDeleteRequest request) {
        return success(adminRoleApplicationService.roleMenus(request.getRoleId()));
    }

    /**
     * 保存角色菜单授权。
     *
     * @param request 菜单授权请求
     * @return 空响应
     */
    @PostMapping("/menus/grant")
    @RequiresPermission("system:role:dataScope")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.UPDATE,
            operation = "保存角色菜单授权", recordRequest = false, recordResponse = false)
    public CommonResult<Void> grantMenus(@Valid @RequestBody SysRoleMenuGrantRequest request) {
        adminRoleApplicationService.grantMenus(request);
        return success();
    }

    /**
     * 查询角色权限授权。
     *
     * @param request 角色标识请求
     * @return 角色权限授权信息
     */
    @PostMapping("/permissions")
    @RequiresPermission("system:role:dataScope")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.QUERY,
            operation = "查询角色权限授权")
    public CommonResult<SysRolePermissionAuthDTO> rolePermissions(@Valid @RequestBody SysRoleDeleteRequest request) {
        return success(adminRoleApplicationService.rolePermissions(request.getRoleId()));
    }

    /**
     * 保存角色权限授权。
     *
     * @param request 权限授权请求
     * @return 空响应
     */
    @PostMapping("/permissions/grant")
    @RequiresPermission("system:role:dataScope")
    @OperationLog(moduleName = "角色管理", businessType = OperationTypeConstants.UPDATE,
            operation = "保存角色权限授权", recordRequest = false, recordResponse = false)
    public CommonResult<Void> grantPermissions(@Valid @RequestBody SysRolePermissionGrantRequest request) {
        adminRoleApplicationService.grantPermissions(request);
        return success();
    }
}
