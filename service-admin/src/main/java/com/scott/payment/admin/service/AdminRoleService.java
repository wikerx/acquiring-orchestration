package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysRoleCreateRequest;
import com.scott.payment.admin.dto.SysRoleDTO;
import com.scott.payment.admin.dto.SysRoleMenuAuthDTO;
import com.scott.payment.admin.dto.SysRoleMenuGrantRequest;
import com.scott.payment.admin.dto.SysRolePermissionAuthDTO;
import com.scott.payment.admin.dto.SysRolePermissionGrantRequest;
import com.scott.payment.admin.dto.SysRoleQueryRequest;
import com.scott.payment.admin.dto.SysRoleStatusRequest;
import com.scott.payment.admin.dto.SysRoleUpdateRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * 后台角色领域服务。
 */
public interface AdminRoleService {

    /**
     * 分页查询后台角色。
     *
     * @param request 查询条件
     * @return 角色分页结果
     */
    PageResult<SysRoleDTO> pageRoles(SysRoleQueryRequest request);

    /**
     * 新增后台角色。
     *
     * @param request 新增请求
     * @return 角色详情
     */
    SysRoleDTO createRole(SysRoleCreateRequest request);

    /**
     * 更新后台角色。
     *
     * @param request 更新请求
     * @return 角色详情
     */
    SysRoleDTO updateRole(SysRoleUpdateRequest request);

    /**
     * 更新后台角色状态。
     *
     * @param request 状态请求
     */
    void updateStatus(SysRoleStatusRequest request);

    /**
     * 删除后台角色。
     *
     * @param roleId 角色主键
     */
    void deleteRole(Long roleId);

    /**
     * 查询角色菜单授权。
     *
     * @param roleId 角色主键
     * @return 菜单授权结果
     */
    SysRoleMenuAuthDTO roleMenus(Long roleId);

    /**
     * 保存角色菜单授权。
     *
     * @param request 菜单授权请求
     */
    void grantMenus(SysRoleMenuGrantRequest request);

    /**
     * 查询角色权限授权。
     *
     * @param roleId 角色主键
     * @return 权限授权结果
     */
    SysRolePermissionAuthDTO rolePermissions(Long roleId);

    /**
     * 保存角色权限授权。
     *
     * @param request 权限授权请求
     */
    void grantPermissions(SysRolePermissionGrantRequest request);
}
