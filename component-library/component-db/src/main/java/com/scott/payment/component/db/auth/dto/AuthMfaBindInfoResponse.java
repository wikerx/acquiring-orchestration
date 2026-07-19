package com.scott.payment.component.db.auth.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthMfaBindInfoResponse
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : OTP 绑定信息响应，位于 component-db 认证 DTO 层；仅在二阶段登录票据有效时返回二维码 URI 和脱敏账号信息。
 * @status : create
 */
@Data
public class AuthMfaBindInfoResponse implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * MFA 类型，本期固定 TOTP。
     */
    private String mfaType;

    /**
     * 绑定状态：PENDING_BIND 或 RESET_REQUIRED。
     */
    private String mfaStatus;

    /**
     * 验证器发行方。
     */
    private String issuer;

    /**
     * 验证器账号标签。
     */
    private String accountLabel;

    /**
     * Google Authenticator 兼容 otpauth URI，前端基于该值本地生成二维码。
     */
    private String otpauthUri;

    /**
     * TOTP 位数。
     */
    private Integer digits;

    /**
     * TOTP 时间步长，单位秒。
     */
    private Integer periodSeconds;

    /**
     * 脱敏后的登录账号。
     */
    private String maskedLoginAccount;

    /**
     * 登录票据过期时间。
     */
    private LocalDateTime loginTicketExpireAt;
}
