package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysRoleCreateRequest;
import com.scott.payment.admin.dto.SysRoleDTO;
import com.scott.payment.admin.dto.SysRoleGrantTreeAuthDTO;
import com.scott.payment.admin.dto.SysRoleGrantTreeSaveRequest;
import com.scott.payment.admin.dto.SysRoleMenuAuthDTO;
import com.scott.payment.admin.dto.SysRoleMenuGrantRequest;
import com.scott.payment.admin.dto.SysRolePermissionAuthDTO;
import com.scott.payment.admin.dto.SysRolePermissionGrantRequest;
import com.scott.payment.admin.dto.SysRoleQueryRequest;
import com.scott.payment.admin.dto.SysRoleStatusRequest;
import com.scott.payment.admin.dto.SysRoleUpdateRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleService
 * @date : 2026-06-19 21:54
 * @email : scott_x@163.com
 * @description : 管理后台角色领域服务
 * @status : create
 *
 * <p>负责后台角色维护、状态切换、菜单授权和权限授权等核心领域规则，不处理控制器协议适配。</p>
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

    /**
     * 查询角色菜单与操作权限统一授权信息。
     *
     * @param roleId 角色主键
     * @return 统一授权信息
     */
    SysRoleGrantTreeAuthDTO roleGrantTree(Long roleId);

    /**
     * 查询新增角色使用的菜单与操作权限授权模板。
     *
     * @return 统一授权模板
     */
    SysRoleGrantTreeAuthDTO roleGrantTreeTemplate();

    /**
     * 原子替换角色的菜单与操作权限授权。
     *
     * @param request 统一授权请求
     */
    void grantRoleTree(SysRoleGrantTreeSaveRequest request);
}
