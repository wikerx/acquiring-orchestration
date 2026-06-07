package com.scott.payment.component.db.auth.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthVerifyCodeSendResponse
 * @date : 2026-06-06 00:00
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
     * 接收方式：SMS、EMAIL、TOTP。
     */
    private String receiverType;

    /**
     * 脱敏后的接收人。
     */
    private String maskedReceiver;

    /**
     * 过期秒数。
     */
    private Integer expireSeconds;

    /**
     * 本地开发联调用验证码。生产环境接入短信/邮件后必须移除。
     */
    private String devCode;
}
