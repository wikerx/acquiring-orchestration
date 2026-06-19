package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountResetPasswordRequest;
import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import com.scott.payment.admin.service.AdminUserService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台用户管理应用服务
 * @status : create
 *
 * <p>当前应用层只负责收敛控制器入口，具体用户、角色、密码和状态规则仍由领域服务承载。</p>
 */
@Service
public class AdminUserApplicationService {

    /**
     * 后台用户领域服务。
     */
    private final AdminUserService adminUserService;

    /**
     * 创建后台用户应用服务。
     *
     * @param adminUserService 后台用户领域服务
     */
    public AdminUserApplicationService(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 分页查询后台用户。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<SysUserAccountDTO> pageUsers(SysUserAccountQueryRequest request) {
        return adminUserService.pageUsers(request);
    }

    /**
     * 新增后台用户。
     *
     * @param request 新增请求
     * @return 新增后的用户
     */
    public SysUserAccountDTO createUser(SysUserAccountCreateRequest request) {
        return adminUserService.createUser(request);
    }

    /**
     * 更新后台用户。
     *
     * @param request 更新请求
     * @return 更新后的用户
     */
    public SysUserAccountDTO updateUser(SysUserAccountUpdateRequest request) {
        return adminUserService.updateUser(request);
    }

    /**
     * 更新后台用户状态。
     *
     * @param request 状态变更请求
     */
    public void updateStatus(SysUserAccountStatusRequest request) {
        adminUserService.updateStatus(request);
    }

    /**
     * 重置后台用户密码。
     *
     * @param request 重置密码请求
     */
    public void resetPassword(SysUserAccountResetPasswordRequest request) {
        adminUserService.resetPassword(request);
    }

    /**
     * 查询后台用户角色授权。
     *
     * @param accountId 用户账号ID
     * @return 角色授权信息
     */
    public SysUserRoleAuthDTO userRoles(Long accountId) {
        return adminUserService.userRoles(accountId);
    }

    /**
     * 分配后台用户角色。
     *
     * @param request 角色分配请求
     */
    public void grantRoles(SysUserRoleGrantRequest request) {
        adminUserService.grantRoles(request);
    }
}
