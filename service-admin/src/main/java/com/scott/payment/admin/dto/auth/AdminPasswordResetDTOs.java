package com.scott.payment.admin.dto.auth;

import com.scott.payment.component.core.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPasswordResetDTOs
 * @date : 2026-10-14 00:00
 * @email : scott_x@163.com
 * @description : 管理后台找回密码接口模型；敏感验证码、TOTP、阶段令牌和新密码仅在请求内短暂使用，禁止日志记录或浏览器持久化。
 * @status : create
 */
public final class AdminPasswordResetDTOs {

    private AdminPasswordResetDTOs() {
    }

    /** 找回密码申请，使用现有图形验证码阻断自动化请求。 */
    @Data
    public static class PasswordResetRequest {
        /** 登录账号，服务端会规范化并仅保存不可逆指纹用于限流。 */
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
        /** 邮件语言，目前支持 zh-CN 与 en-US。 */
        @Pattern(regexp = "^(zh-CN|en-US)?$", message = "locale is invalid")
        private String locale;
    }

    /** 申请响应，对任何账号状态均保持相同字段和文案。 */
    @Data
    public static class PasswordResetRequestResponse {
        /** 高熵请求标识，不包含账号或邮箱信息。 */
        private String requestId;
        /** 邮箱验证码有效秒数。 */
        private Integer expireSeconds;
        /** 防账号枚举的固定提示。 */
        private String message;
    }

    /** 邮箱验证码校验请求。 */
    @Data
    public static class PasswordResetEmailVerifyRequest {
        /** 找回密码请求标识。 */
        @NotBlank(message = "requestId is required")
        private String requestId;
        /** 六位大小写敏感字母数字邮件验证码明文，禁止记录。 */
        @NotBlank(message = "emailCode is required")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z0-9]{6}$",
                message = "emailCode must contain 6 case-sensitive letters and digits")
        private String emailCode;
    }

    /** 邮箱或 MFA 验证后的下一步响应。 */
    @Data
    public static class PasswordResetVerifyResponse {
        /** 下一步：MFA、RESET_PASSWORD 或 ADMIN_SUPPORT。 */
        private String nextStep;
        /** 下一阶段的一次性令牌；服务端仅保存 SHA-256 哈希。 */
        private String actionToken;
        /** 阶段令牌剩余有效秒数。 */
        private Integer expireSeconds;
        /** 当前阶段提示，不包含账号状态细节。 */
        private String message;
    }

    /** 已启用 MFA 账号的 TOTP 校验请求。 */
    @Data
    public static class PasswordResetMfaVerifyRequest {
        /** 找回密码请求标识。 */
        @NotBlank(message = "requestId is required")
        private String requestId;
        /** 邮箱验证后签发的一次性阶段令牌。 */
        @NotBlank(message = "actionToken is required")
        private String actionToken;
        /** 六位 TOTP 验证码明文，禁止记录。 */
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
        /** 身份校验完成后签发的一次性重置令牌。 */
        @NotBlank(message = "resetToken is required")
        private String resetToken;
        /** 新密码，必须同时包含大小写字母、数字和特殊字符。 */
        @NotBlank(message = "newPassword is required")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "newPassword length must be between 12 and 64")
        @Pattern(regexp = PasswordPolicy.REGEX, message = "newPassword complexity is insufficient")
        private String newPassword;
    }

    /** 密码重置完成响应。 */
    @Data
    public static class PasswordResetConfirmResponse {
        /** 固定完成提示，前端完成后返回登录页且不得自动登录。 */
        private String message;
    }
}
