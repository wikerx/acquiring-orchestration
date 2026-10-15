package com.scott.payment.admin.application.auth;

import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetVerifyResponse;
import com.scott.payment.admin.service.AdminPasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPasswordResetApplicationService
 * @date : 2026-10-14 00:00
 * @email : scott_x@163.com
 * @description : 管理后台找回密码应用服务，负责从 HTTP 请求提取安全上下文并将四阶段用例交给业务服务执行。
 * @status : create
 */
@Service
public class AdminPasswordResetApplicationService {

    private final AdminPasswordResetService passwordResetService;

    /** 创建找回密码应用服务。 */
    public AdminPasswordResetApplicationService(AdminPasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** 创建邮箱验证请求。 */
    public PasswordResetRequestResponse requestReset(PasswordResetRequest request, HttpServletRequest servletRequest) {
        return passwordResetService.requestReset(
                request,
                AdminClientIpResolver.resolve(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
    }

    /** 校验邮件验证码。 */
    public PasswordResetVerifyResponse verifyEmail(PasswordResetEmailVerifyRequest request, HttpServletRequest servletRequest) {
        return passwordResetService.verifyEmail(request, AdminClientIpResolver.resolve(servletRequest));
    }

    /** 校验 MFA 动态验证码。 */
    public PasswordResetVerifyResponse verifyMfa(PasswordResetMfaVerifyRequest request, HttpServletRequest servletRequest) {
        return passwordResetService.verifyMfa(request, AdminClientIpResolver.resolve(servletRequest));
    }

    /** 确认设置新密码。 */
    public PasswordResetConfirmResponse confirm(PasswordResetConfirmRequest request, HttpServletRequest servletRequest) {
        return passwordResetService.confirm(request, AdminClientIpResolver.resolve(servletRequest));
    }
}
