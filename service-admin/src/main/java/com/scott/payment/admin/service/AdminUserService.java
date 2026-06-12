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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserService
 * @date : 2026-06-06 00:00
 * @description : 管理后台用户服务
 * @status : create
 */
public interface AdminUserService {

    PageResult<SysUserAccountDTO> pageUsers(SysUserAccountQueryRequest request);

    SysUserAccountDTO createUser(SysUserAccountCreateRequest request);

    SysUserAccountDTO updateUser(SysUserAccountUpdateRequest request);

    void updateStatus(SysUserAccountStatusRequest request);

    void resetPassword(SysUserAccountResetPasswordRequest request);

    SysUserRoleAuthDTO userRoles(Long accountId);

    void grantRoles(SysUserRoleGrantRequest request);
}
