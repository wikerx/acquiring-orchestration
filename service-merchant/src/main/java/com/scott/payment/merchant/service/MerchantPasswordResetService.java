package com.scott.payment.merchant.service;

import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetVerifyResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPasswordResetService
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户系统安全找回密码服务，封装租户边界、账号枚举防护、MFA 和一次性令牌规则。
 * @status : create
 */
public interface MerchantPasswordResetService {

    /** 创建匿名找回密码请求。 */
    PasswordResetRequestResponse requestReset(PasswordResetRequest request, String clientIp, String userAgent);

    /** 商户管理员为当前商户指定员工签发一次性重置邮件。 */
    PasswordResetRequestResponse requestResetForAccount(Long accountId, String locale,
                                                        String clientIp, String userAgent);

    /** 校验邮件验证码并决定下一安全阶段。 */
    PasswordResetVerifyResponse verifyEmail(PasswordResetEmailVerifyRequest request, String clientIp);

    /** 校验已绑定账号的 TOTP 并签发最终重置令牌。 */
    PasswordResetVerifyResponse verifyMfa(PasswordResetMfaVerifyRequest request, String clientIp);

    /** 原子消费重置令牌、更新密码并注销全部旧会话。 */
    PasswordResetConfirmResponse confirm(PasswordResetConfirmRequest request, String clientIp);
}
