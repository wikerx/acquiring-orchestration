package com.scott.payment.merchant.application.auth;

import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteResponse;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyResponse;
import com.scott.payment.merchant.service.MerchantEmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/** 商户账号邮箱验证应用服务，负责提取可信客户端 IP 并编排公开验证流程。 */
@Service
public class MerchantEmailVerificationApplicationService {

    private final MerchantEmailVerificationService emailVerificationService;

    /** 创建邮箱验证应用服务。 */
    public MerchantEmailVerificationApplicationService(MerchantEmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    /** 校验邮件验证码。 */
    public EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request,
                                                   HttpServletRequest servletRequest) {
        return emailVerificationService.verify(request, MerchantClientIpResolver.resolve(servletRequest));
    }

    /** 完成账号首次设密和激活。 */
    public AccountActivationCompleteResponse completeActivation(AccountActivationCompleteRequest request,
                                                                 HttpServletRequest servletRequest) {
        return emailVerificationService.completeActivation(request, MerchantClientIpResolver.resolve(servletRequest));
    }
}
