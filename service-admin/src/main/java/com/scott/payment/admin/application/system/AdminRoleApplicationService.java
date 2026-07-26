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
@Service
public class AdminRoleApplicationService {

    /**
     * admin Role Service 依赖，用于 Admin Role Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
