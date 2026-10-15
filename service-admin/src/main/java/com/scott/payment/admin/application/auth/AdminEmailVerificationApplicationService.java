package com.scott.payment.admin.application.auth;

import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteResponse;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyResponse;
import com.scott.payment.admin.service.AdminEmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailVerificationApplicationService
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 管理账号邮箱验证应用服务，负责提取可信客户端 IP 并编排公开验证码校验与账号激活用例。
 * @status : create
 */
@Service
public class AdminEmailVerificationApplicationService {

    private final AdminEmailVerificationService emailVerificationService;

    /** 创建应用服务。 */
    public AdminEmailVerificationApplicationService(AdminEmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    /** 校验邮件验证码。 */
    public EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request,
                                                   HttpServletRequest servletRequest) {
        return emailVerificationService.verify(request, AdminClientIpResolver.resolve(servletRequest));
    }

    /** 完成账号首次设密和激活。 */
    public AccountActivationCompleteResponse completeActivation(AccountActivationCompleteRequest request,
                                                                 HttpServletRequest servletRequest) {
        return emailVerificationService.completeActivation(request, AdminClientIpResolver.resolve(servletRequest));
    }
}
