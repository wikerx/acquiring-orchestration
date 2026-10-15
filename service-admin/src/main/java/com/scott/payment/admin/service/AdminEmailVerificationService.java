package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteResponse;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailVerificationService
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 管理账号激活和邮箱所有权验证领域服务，统一验证码签发、验证、首次设密与重放防护规则。
 * @status : create
 */
public interface AdminEmailVerificationService {

    String PURPOSE_ACCOUNT_ACTIVATION = "ACCOUNT_ACTIVATION";
    String PURPOSE_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    String PURPOSE_EMAIL_CHANGE = "EMAIL_CHANGE";

    /**
     * 为指定账号创建新的邮箱验证请求并在事务提交后发送邮件。
     *
     * @param accountId 账号主键
     * @param purpose 验证用途
     * @param locale 邮件语言
     * @param requestIp 发起 IP
     * @param userAgent 客户端标识
     * @param enforceRateLimit 是否应用人工重发限流
     */
    void issue(Long accountId, String purpose, String locale, String requestIp, String userAgent,
               boolean enforceRateLimit);

    /** 校验邮箱验证码，并按用途完成验证或签发首次设密令牌。 */
    EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request, String clientIp);

    /** 原子消费动作令牌，完成新账号首次设密和激活。 */
    AccountActivationCompleteResponse completeActivation(AccountActivationCompleteRequest request, String clientIp);
}
