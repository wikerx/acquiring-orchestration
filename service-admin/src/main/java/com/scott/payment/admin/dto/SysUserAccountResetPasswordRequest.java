package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountResetPasswordRequest
 * @date : 2026-06-07 00:00
 * @description : 管理后台用户重置密码请求
 * @status : create
 */
@Data
public class SysUserAccountResetPasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "accountId")
    private Long accountId;

    @NotBlank(message = "password")
    @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
    private String password;
}
