package com.scott.payment.merchant.application.auth;

import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetVerifyResponse;
import com.scott.payment.merchant.service.MerchantPasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPasswordResetApplicationService
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户找回密码应用服务，负责提取可信客户端上下文并编排四阶段安全流程。
 * @status : create
 */
@Service
public class MerchantPasswordResetApplicationService {

    private final MerchantPasswordResetService passwordResetService;

    /** 创建商户找回密码应用服务。 */
    public MerchantPasswordResetApplicationService(MerchantPasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** 创建找回密码申请。 */
    public PasswordResetRequestResponse requestReset(PasswordResetRequest request, HttpServletRequest servletRequest) {
        return passwordResetService.requestReset(request, MerchantClientIpResolver.resolve(servletRequest),
                servletRequest.getHeader("User-Agent"));
    }

    /** 校验邮件验证码。 */
    public PasswordResetVerifyResponse verifyEmail(PasswordResetEmailVerifyRequest request,
                                                   HttpServletRequest servletRequest) {
        return passwordResetService.verifyEmail(request, MerchantClientIpResolver.resolve(servletRequest));
    }

    /** 校验 TOTP。 */
    public PasswordResetVerifyResponse verifyMfa(PasswordResetMfaVerifyRequest request,
                                                 HttpServletRequest servletRequest) {
        return passwordResetService.verifyMfa(request, MerchantClientIpResolver.resolve(servletRequest));
    }

    /** 原子消费重置令牌并更新密码。 */
    public PasswordResetConfirmResponse confirm(PasswordResetConfirmRequest request,
                                                HttpServletRequest servletRequest) {
        return passwordResetService.confirm(request, MerchantClientIpResolver.resolve(servletRequest));
    }
}
