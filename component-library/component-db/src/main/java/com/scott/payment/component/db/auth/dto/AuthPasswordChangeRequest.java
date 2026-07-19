package com.scott.payment.component.db.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthPasswordChangeRequest
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 当前登录账号修改密码请求，位于 component-db 认证传输层；请求体包含敏感明文密码，调用方不得记录请求内容。
 * @status : create
 */
@Data
public class AuthPasswordChangeRequest implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 当前登录密码，用于修改密码前校验身份。
     */
    @NotBlank(message = "oldPassword is required")
    @Size(min = 8, max = 64, message = "oldPassword length must be between 8 and 64")
    private String oldPassword;

    /**
     * 新登录密码，修改成功后会重新生成密码盐和 PBKDF2 哈希。
     */
    @NotBlank(message = "newPassword is required")
    @Size(min = 8, max = 64, message = "newPassword length must be between 8 and 64")
    private String newPassword;
}
