package com.scott.payment.component.db.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthRegisterRequest
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统账号注册请求
 * @status : create
 */
@Data
public class AuthRegisterRequest implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 登录账号。
     */
    @NotBlank(message = "loginAccount")
    @Size(max = 100, message = "loginAccount length must be less than 100")
    private String loginAccount;

    /**
     * 登录密码。
     */
    @NotBlank(message = "password")
    @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
    private String password;

    /**
     * 用户姓名。
     */
    @NotBlank(message = "realName")
    @Size(max = 100, message = "realName length must be less than 100")
    private String realName;

    /**
     * 手机号。
     */
    @Size(max = 30, message = "mobile length must be less than 30")
    private String mobile;

    /**
     * 邮箱。
     */
    @Email(message = "email format does not match")
    @Size(max = 150, message = "email length must be less than 150")
    private String email;

    /**
     * 商户号，商户系统账号必填，必须对应已有 base_merchant_info。
     */
    private String merchantId;

    /**
     * 角色编码，不传时按应用分配默认角色。
     */
    private String roleCode;

    /**
     * 操作人。
     */
    private String operator;
}
