package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetVerifyResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPasswordResetService
 * @date : 2026-10-14 00:00
 * @email : scott_x@163.com
 * @description : 管理后台自助找回密码服务，封装账号枚举防护、邮箱验证、MFA、一次性令牌和会话撤销规则。
 * @status : create
 */
public interface AdminPasswordResetService {

    /** 创建找回密码请求并按资格异步触发验证码邮件。 */
    PasswordResetRequestResponse requestReset(PasswordResetRequest request, String clientIp, String userAgent);

    /** 管理员为指定账号签发同一套一次性邮件重置流程，不接收或生成明文密码。 */
    PasswordResetRequestResponse requestResetForAccount(Long accountId, String locale,
                                                        String clientIp, String userAgent);

    /** 校验邮件验证码并决定下一安全阶段。 */
    PasswordResetVerifyResponse verifyEmail(PasswordResetEmailVerifyRequest request, String clientIp);

    /** 校验已绑定账号的 TOTP 并签发最终重置令牌。 */
    PasswordResetVerifyResponse verifyMfa(PasswordResetMfaVerifyRequest request, String clientIp);

    /** 原子消费重置令牌、更新密码并注销全部会话。 */
    PasswordResetConfirmResponse confirm(PasswordResetConfirmRequest request, String clientIp);
}
