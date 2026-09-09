package com.scott.payment.component.db.auth.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthVerifyCodeSendResponse
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 登录动态验证码发送响应
 * @status : create
 */
@Data
public class AuthVerifyCodeSendResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 验证码记录ID。
     */
    private String verifyCodeId;

    /**
     * 验证码类型：CAPTCHA 表示登录页图形验证码；历史兼容 SMS、EMAIL、TOTP。
     */
    private String receiverType;

    /**
     * 脱敏后的接收人。图形验证码场景固定返回页面验证码文案，不包含账号、邮箱或手机号。
     */
    private String maskedReceiver;

    /**
     * 图形验证码图片，格式为 data:image/png;base64,...。生产环境只返回图片，不返回明文验证码。
     */
    private String captchaImage;

    /**
     * 过期秒数。
     */
    private Integer expireSeconds;

}
