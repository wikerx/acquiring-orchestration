package com.scott.payment.merchant.dto.auth;

import com.scott.payment.component.core.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailVerificationDTOs
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户账号激活和邮箱验证接口模型；验证码、动作令牌与首次密码禁止记录明文。
 * @status : create
 */
public final class MerchantEmailVerificationDTOs {

    private MerchantEmailVerificationDTOs() {
    }

    /** 邮箱验证码校验请求。 */
    @Data
    public static class EmailVerificationVerifyRequest {
        @NotBlank(message = "requestId is required")
        private String requestId;
        @NotBlank(message = "emailCode is required")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z0-9]{6}$",
                message = "emailCode format is invalid")
        private String emailCode;
    }

    /** 邮箱验证结果。 */
    @Data
    public static class EmailVerificationVerifyResponse {
        private String nextStep;
        private String actionToken;
        private Integer expireSeconds;
        private String message;
    }

    /** 新账号首次设密和激活请求。 */
    @Data
    public static class AccountActivationCompleteRequest {
        @NotBlank(message = "requestId is required")
        private String requestId;
        @NotBlank(message = "actionToken is required")
        private String actionToken;
        @NotBlank(message = "newPassword is required")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = "newPassword length must be between 12 and 64")
        @Pattern(regexp = PasswordPolicy.REGEX, message = "newPassword complexity is insufficient")
        private String newPassword;
    }

    /** 账号激活完成响应。 */
    @Data
    public static class AccountActivationCompleteResponse {
        private String message;
    }
}
