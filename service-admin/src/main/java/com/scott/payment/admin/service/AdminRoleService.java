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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleService
 * @date : 2026-06-07 00:00
 * @description : 管理后台角色服务
 * @status : create
 */
public interface AdminRoleService {

    PageResult<SysRoleDTO> pageRoles(SysRoleQueryRequest request);

    SysRoleDTO createRole(SysRoleCreateRequest request);

    SysRoleDTO updateRole(SysRoleUpdateRequest request);

    void updateStatus(SysRoleStatusRequest request);

    void deleteRole(Long roleId);

    SysRoleMenuAuthDTO roleMenus(Long roleId);

    void grantMenus(SysRoleMenuGrantRequest request);

    SysRolePermissionAuthDTO rolePermissions(Long roleId);

    void grantPermissions(SysRolePermissionGrantRequest request);
}
