package com.scott.payment.component.db.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthLoginRequest
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统登录请求
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthLoginRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Auth Login 请求对象，位于 component-library/component-db 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class AuthLoginRequest implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 登录账号。
     */
    @NotBlank(message = "loginAccount")
    private String loginAccount;

    /**
     * 登录密码。
     */
    @NotBlank(message = "password")
    private String password;

    /**
     * 商户号，商户系统登录时可传，用于进一步限制账号必须属于该商户。
     */
    private String merchantId;

    /**
     * 动态验证码记录ID。
     */
    @NotBlank(message = "verifyCodeId")
    private String verifyCodeId;

    /**
     * 动态验证码。
     */
    @NotBlank(message = "verifyCode")
    private String verifyCode;
}
