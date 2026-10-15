package com.scott.payment.merchant.service;

import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteResponse;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailVerificationService
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户账号激活和邮箱所有权验证服务，统一签发、校验、首次设密和重放防护规则。
 * @status : create
 */
public interface MerchantEmailVerificationService {

    String PURPOSE_ACCOUNT_ACTIVATION = "ACCOUNT_ACTIVATION";
    String PURPOSE_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    String PURPOSE_EMAIL_CHANGE = "EMAIL_CHANGE";

    /** 为指定商户账号签发邮箱验证请求。 */
    void issue(Long accountId, String purpose, String locale, String requestIp, String userAgent,
               boolean enforceRateLimit);

    /** 校验邮箱验证码。 */
    EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request, String clientIp);

    /** 原子消费动作令牌并完成首次设密和激活。 */
    AccountActivationCompleteResponse completeActivation(AccountActivationCompleteRequest request, String clientIp);
}
