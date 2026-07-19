package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaActionRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaExemptRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogQuery;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogResponse;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaStatusResponse;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserMfaService
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 后台用户 MFA 领域服务，位于 service-admin 服务契约层；定义 OTP 开启、重置、豁免、禁用、解锁和日志查询边界。
 * @status : create
 */
public interface AdminUserMfaService {

    /**
     * 强制启用用户 OTP。
     *
     * @param request 操作请求
     * @return 更新后的 MFA 状态
     */
    UserMfaStatusResponse requireMfa(UserMfaActionRequest request);

    /**
     * 重置用户 OTP。
     *
     * @param request 操作请求
     * @return 更新后的 MFA 状态
     */
    UserMfaStatusResponse resetMfa(UserMfaActionRequest request);

    /**
     * 配置用户 OTP 豁免。
     *
     * @param request 豁免请求
     * @return 更新后的 MFA 状态
     */
    UserMfaStatusResponse exemptMfa(UserMfaExemptRequest request);

    /**
     * 停用用户 OTP。
     *
     * @param request 操作请求
     * @return 更新后的 MFA 状态
     */
    UserMfaStatusResponse disableMfa(UserMfaActionRequest request);

    /**
     * 解锁用户 OTP。
     *
     * @param request 操作请求
     * @return 更新后的 MFA 状态
     */
    UserMfaStatusResponse unlockMfa(UserMfaActionRequest request);

    /**
     * 重新发送 OTP 绑定邮件。
     *
     * @param request 操作请求
     * @return 更新后的 MFA 状态
     */
    UserMfaStatusResponse resendBindMail(UserMfaActionRequest request);

    /**
     * 分页查询 OTP 安全日志。
     *
     * @param query 查询条件
     * @return 日志分页结果
     */
    PageResult<UserMfaLogResponse> pageLogs(UserMfaLogQuery query);
}
