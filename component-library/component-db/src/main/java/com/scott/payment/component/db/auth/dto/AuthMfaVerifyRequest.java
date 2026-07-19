package com.scott.payment.component.db.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthMfaVerifyRequest
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : OTP 登录验证请求，位于 component-db 认证 DTO 层；使用短期登录票据和动态验证码换取真实登录会话。
 * @status : create
 */
@Data
public class AuthMfaVerifyRequest implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 账号密码校验通过后返回的短期登录票据。
     */
    @NotBlank(message = "loginTicket is required")
    private String loginTicket;

    /**
     * Google Authenticator 当前 6 位动态验证码。
     */
    @NotBlank(message = "totpCode is required")
    @Pattern(regexp = "\\d{6}", message = "totpCode must be 6 digits")
    private String totpCode;
}
