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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthVerifyCodeSendRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Auth Verify Code Send 请求对象，位于 component-library/component-db 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class AuthVerifyCodeSendRequest implements Serializable {

    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
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
