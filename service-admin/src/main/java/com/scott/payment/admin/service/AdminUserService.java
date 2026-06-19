package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountResetPasswordRequest;
import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserService
 * @date : 2026-06-19 21:54
 * @email : scott_x@163.com
 * @description : 管理后台用户领域服务
 * @status : create
 *
 * <p>负责后台用户维护、状态变更、密码重置和角色授权等核心领域规则，不处理控制器协议适配。</p>
 */
public interface AdminUserService {

    /**
     * 分页查询后台用户。
     *
     * @param request 查询条件
     * @return 用户分页结果
     */
    PageResult<SysUserAccountDTO> pageUsers(SysUserAccountQueryRequest request);

    /**
     * 按条件查询导出用后台用户列表。
     *
     * @param request 查询条件
     * @return 用户列表
     */
    List<SysUserAccountDTO> listUsers(SysUserAccountQueryRequest request);

    /**
     * 新增后台用户。
     *
     * @param request 新增请求
     * @return 用户详情
     */
    SysUserAccountDTO createUser(SysUserAccountCreateRequest request);

    /**
     * 更新后台用户。
     *
     * @param request 更新请求
     * @return 用户详情
     */
    SysUserAccountDTO updateUser(SysUserAccountUpdateRequest request);

    /**
     * 更新后台用户状态。
     *
     * @param request 状态请求
     */
    void updateStatus(SysUserAccountStatusRequest request);

    /**
     * 重置后台用户密码。
     *
     * @param request 重置密码请求
     */
    void resetPassword(SysUserAccountResetPasswordRequest request);

    /**
     * 查询后台用户角色授权。
     *
     * @param accountId 账号主键
     * @return 角色授权信息
     */
    SysUserRoleAuthDTO userRoles(Long accountId);

    /**
     * 保存后台用户角色授权。
     *
     * @param request 角色授权请求
     */
    void grantRoles(SysUserRoleGrantRequest request);

    /**
     * 逻辑删除后台用户。
     *
     * @param accountIds 账号主键列表
     */
    void removeUsers(List<Long> accountIds);
}
