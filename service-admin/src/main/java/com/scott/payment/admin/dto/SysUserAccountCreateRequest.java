package com.scott.payment.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountCreateRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户新增请求 DTO
 * @status : create
 *
 * <p>用于新增后台用户账号，承载登录账号、登录密码和基础联系人信息。</p>
 */
@Data
public class SysUserAccountCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "loginAccount")
    @Size(max = 100, message = "loginAccount length must be less than 100")
    private String loginAccount;

    @NotBlank(message = "password")
    @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
    private String password;

    @NotBlank(message = "realName")
    @Size(max = 100, message = "realName length must be less than 100")
    private String realName;

    @Size(max = 30, message = "mobile length must be less than 30")
    private String mobile;

    @Email(message = "email format does not match")
    @Size(max = 150, message = "email length must be less than 150")
    private String email;
}
