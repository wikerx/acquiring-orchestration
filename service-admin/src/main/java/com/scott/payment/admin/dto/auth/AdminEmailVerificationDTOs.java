package com.scott.payment.admin.dto.auth;

import com.scott.payment.component.core.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailVerificationDTOs
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 管理账号激活和邮箱验证接口模型；验证码、动作令牌与首次密码仅在请求期间使用，禁止持久化明文或写入日志。
 * @status : create
 */
public final class AdminEmailVerificationDTOs {

    private AdminEmailVerificationDTOs() {
    }

    /** 邮箱验证码校验请求。 */
    @Data
    public static class EmailVerificationVerifyRequest {
        /** 邮件链接携带的高熵公开请求编号。 */
        @NotBlank(message = "requestId is required")
        private String requestId;
        /** 六位大小写敏感字母数字验证码明文，禁止记录。 */
        @NotBlank(message = "emailCode is required")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z0-9]{6}$",
                message = "emailCode format is invalid")
        private String emailCode;
    }

    /** 邮箱验证结果；激活账号时继续进入首次密码设置阶段。 */
    @Data
    public static class EmailVerificationVerifyResponse {
        /** 下一步：SET_PASSWORD 或 COMPLETED。 */
        private String nextStep;
        /** 激活账号设置首次密码所需的一次性短期令牌。 */
        private String actionToken;
        /** 动作令牌剩余有效秒数；直接完成验证时为零。 */
        private Integer expireSeconds;
        /** 稳定业务提示，不包含邮箱地址等敏感信息。 */
        private String message;
    }

    /** 新账号完成激活并设置首次密码请求。 */
    @Data
    public static class AccountActivationCompleteRequest {
        /** 邮件链接携带的高熵公开请求编号。 */
        @NotBlank(message = "requestId is required")
        private String requestId;
        /** 邮箱验证码通过后签发的一次性动作令牌。 */
        @NotBlank(message = "actionToken is required")
        private String actionToken;
        /** 首次登录密码，必须满足管理系统高强度密码规则。 */
        @NotBlank(message = "newPassword is required")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "newPassword length must be between 12 and 64")
        @Pattern(regexp = PasswordPolicy.REGEX, message = "newPassword complexity is insufficient")
        private String newPassword;
    }

    /** 账号激活完成响应。 */
    @Data
    public static class AccountActivationCompleteResponse {
        /** 固定完成提示；完成后返回登录页并继续 MFA 登录流程。 */
        private String message;
    }
}
