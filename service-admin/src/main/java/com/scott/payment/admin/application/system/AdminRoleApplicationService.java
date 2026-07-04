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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台角色管理应用服务
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Admin Role Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminRoleApplicationService {

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 查询系统管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 创建或保存系统管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysRoleDTO updateRole(SysRoleUpdateRequest request) {
        return adminRoleService.updateRole(request);
    }

    /**
     * 更新角色状态。
     *
     * @param request 状态请求
     */
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void updateStatus(SysRoleStatusRequest request) {
        adminRoleService.updateStatus(request);
    }

    /**
     * 删除角色。
     *
     * @param roleId 角色主键
     */
    /**
     * 删除系统管理数据，按业务规则处理引用校验和删除边界。
     * @param roleId 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param roleId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysRoleMenuAuthDTO roleMenus(Long roleId) {
        return adminRoleService.roleMenus(roleId);
    }

    /**
     * 保存角色菜单授权。
     *
     * @param request 菜单授权请求
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param roleId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public SysRolePermissionAuthDTO rolePermissions(Long roleId) {
        return adminRoleService.rolePermissions(roleId);
    }

    /**
     * 保存角色权限授权。
     *
     * @param request 权限授权请求
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void grantPermissions(SysRolePermissionGrantRequest request) {
        adminRoleService.grantPermissions(request);
    }
}
