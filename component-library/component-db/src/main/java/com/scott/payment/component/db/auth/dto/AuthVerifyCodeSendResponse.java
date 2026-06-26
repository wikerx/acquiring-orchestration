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
     * 调试阶段返回给前端的验证码明文，当前开发、测试、生产环境保持一致。
     */
    private String devCode;
}
