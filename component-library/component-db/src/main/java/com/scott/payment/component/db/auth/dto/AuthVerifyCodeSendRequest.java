package com.scott.payment.component.db.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthVerifyCodeSendRequest
 * @date : 2026-06-06 00:00
 * @description : 登录动态验证码发送请求
 * @status : create
 */
@Data
public class AuthVerifyCodeSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 登录账号。图形验证码生成不依赖账号，兼容旧前端传入但不作为身份校验依据。
     */
    private String loginAccount;

    /**
     * 验证码场景，登录固定为 LOGIN。
     */
    @NotBlank(message = "scene")
    private String scene;

    /**
     * 商户号，商户系统登录时可传。
     */
    private String merchantId;
}
