package com.scott.payment.merchant.dto.auth;

import com.scott.payment.component.core.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPasswordResetDTOs
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户系统找回密码接口模型；商户号与登录账号共同定位租户账号，敏感值禁止记录。
 * @status : create
 */
public final class MerchantPasswordResetDTOs {

    private MerchantPasswordResetDTOs() {
    }

    /** 商户找回密码申请。 */
    @Data
    public static class PasswordResetRequest {
        /** 商户号，与登录账号联合定位商户成员。 */
        @NotBlank(message = "merchantId is required")
        @Size(max = 32, message = "merchantId length must not exceed 32")
        private String merchantId;
        /** 商户内登录账号。 */
        @NotBlank(message = "loginAccount is required")
        @Size(max = 100, message = "loginAccount length must not exceed 100")
        private String loginAccount;
        /** 图形验证码记录 ID。 */
        @NotBlank(message = "verifyCodeId is required")
        private String verifyCodeId;
        /** 图形验证码明文，禁止记录。 */
        @NotBlank(message = "verifyCode is required")
        @Size(max = 10, message = "verifyCode length must not exceed 10")
        private String verifyCode;
        /** 邮件语言，目前支持 zh-CN 和 en-US。 */
        @Pattern(regexp = "^(zh-CN|en-US)?$", message = "locale is invalid")
        private String locale;
    }

    /** 申请响应，对真实账号和虚拟请求保持同一结构。 */
    @Data
    public static class PasswordResetRequestResponse {
        /** 高熵请求标识，不包含商户号或账号信息。 */
        private String requestId;
        /** 邮件验证码有效秒数。 */
        private Integer expireSeconds;
        /** 防账号枚举的统一提示。 */
        private String message;
    }

    /** 邮件验证码校验请求。 */
    @Data
    public static class PasswordResetEmailVerifyRequest {
        /** 找回密码请求标识。 */
        @NotBlank(message = "requestId is required")
        private String requestId;
        /** 6 位大小写敏感字母数字验证码。 */
        @NotBlank(message = "emailCode is required")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z0-9]{6}$",
                message = "emailCode format is invalid")
        private String emailCode;
    }

    /** 邮箱或 MFA 验证后的下一阶段。 */
    @Data
    public static class PasswordResetVerifyResponse {
        /** 下一步：MFA、RESET_PASSWORD 或 MERCHANT_SUPPORT。 */
        private String nextStep;
        /** 下一阶段一次性令牌；服务端只保存哈希。 */
        private String actionToken;
        /** 阶段令牌剩余有效秒数。 */
        private Integer expireSeconds;
        /** 不含账号敏感信息的稳定提示。 */
        private String message;
    }

    /** 商户账号 TOTP 校验请求。 */
    @Data
    public static class PasswordResetMfaVerifyRequest {
        /** 找回密码请求标识。 */
        @NotBlank(message = "requestId is required")
        private String requestId;
        /** 邮箱验证后签发的一次性阶段令牌。 */
        @NotBlank(message = "actionToken is required")
        private String actionToken;
        /** 6 位 TOTP 验证码。 */
        @NotBlank(message = "totpCode is required")
        @Pattern(regexp = "^\\d{6}$", message = "totpCode must be 6 digits")
        private String totpCode;
    }

    /** 最终设置新密码请求。 */
    @Data
    public static class PasswordResetConfirmRequest {
        /** 找回密码请求标识。 */
        @NotBlank(message = "requestId is required")
        private String requestId;
        /** 身份验证完成后签发的一次性重置令牌。 */
        @NotBlank(message = "resetToken is required")
        private String resetToken;
        /** 新密码，必须符合统一高强度密码策略。 */
        @NotBlank(message = "newPassword is required")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "newPassword length must be between 12 and 64")
        @Pattern(regexp = PasswordPolicy.REGEX, message = "newPassword complexity is insufficient")
        private String newPassword;
    }

    /** 密码重置完成响应。 */
    @Data
    public static class PasswordResetConfirmResponse {
        /** 固定完成提示，完成后需返回登录页。 */
        private String message;
    }
}
