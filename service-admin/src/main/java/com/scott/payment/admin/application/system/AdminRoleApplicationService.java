package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysRoleCreateRequest;
import com.scott.payment.admin.dto.SysRoleDTO;
import com.scott.payment.admin.dto.SysRoleMenuAuthDTO;
import com.scott.payment.admin.dto.SysRoleMenuGrantRequest;
import com.scott.payment.admin.dto.SysRolePermissionAuthDTO;
import com.scott.payment.admin.dto.SysRolePermissionGrantRequest;
import com.scott.payment.admin.dto.SysRoleQueryRequest;
import com.scott.payment.admin.dto.SysRoleStatusRequest;
import com.scott.payment.admin.dto.SysRoleUpdateRequest;
import com.scott.payment.admin.service.AdminRoleService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 后台角色应用服务。
 */
@Service
public class AdminRoleApplicationService {

    private final AdminRoleService adminRoleService;

    /**
     * 创建后台角色应用服务。
     *
     * @param adminRoleService 角色领域服务
     */
    public AdminRoleApplicationService(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    /**
     * 分页查询角色。
     *
     * @param request 查询条件
     * @return 角色分页结果
     */
    public PageResult<SysRoleDTO> pageRoles(SysRoleQueryRequest request) {
        return adminRoleService.pageRoles(request);
    }

    /**
     * 新增角色。
     *
     * @param request 新增请求
     * @return 角色详情
     */
    public SysRoleDTO createRole(SysRoleCreateRequest request) {
        return adminRoleService.createRole(request);
    }

    /**
     * 更新角色。
     *
     * @param request 更新请求
     * @return 角色详情
     */
    public SysRoleDTO updateRole(SysRoleUpdateRequest request) {
        return adminRoleService.updateRole(request);
    }

    /**
     * 更新角色状态。
     *
     * @param request 状态请求
     */
    public void updateStatus(SysRoleStatusRequest request) {
        adminRoleService.updateStatus(request);
    }

    /**
     * 删除角色。
     *
     * @param roleId 角色主键
     */
    public void deleteRole(Long roleId) {
        adminRoleService.deleteRole(roleId);
    }

    /**
     * 查询角色菜单授权。
     *
     * @param roleId 角色主键
     * @return 菜单授权详情
     */
    public SysRoleMenuAuthDTO roleMenus(Long roleId) {
        return adminRoleService.roleMenus(roleId);
    }

    /**
     * 保存角色菜单授权。
     *
     * @param request 菜单授权请求
     */
    public void grantMenus(SysRoleMenuGrantRequest request) {
        adminRoleService.grantMenus(request);
    }

    /**
     * 查询角色权限授权。
     *
     * @param roleId 角色主键
     * @return 权限授权详情
     */
    public SysRolePermissionAuthDTO rolePermissions(Long roleId) {
        return adminRoleService.rolePermissions(roleId);
    }

    /**
     * 保存角色权限授权。
     *
     * @param request 权限授权请求
     */
    public void grantPermissions(SysRolePermissionGrantRequest request) {
        adminRoleService.grantPermissions(request);
    }
}
