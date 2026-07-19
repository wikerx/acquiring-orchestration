package com.scott.payment.component.db.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthProfileUpdateRequest
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 当前登录账号个人资料更新请求，位于 component-db 认证传输层，仅允许修改本人昵称、手机号和邮箱。
 * @status : create
 */
@Data
public class AuthProfileUpdateRequest implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户昵称，用于个人中心展示；不能为空，最长 100 位。
     */
    @NotBlank(message = "nickname is required")
    @Size(max = 100, message = "nickname length must be less than 100")
    private String nickname;

    /**
     * 联系手机号，可为空，最长 30 位；同时同步到登录账号冗余字段。
     */
    @Size(max = 30, message = "mobile length must be less than 30")
    private String mobile;

    /**
     * 联系邮箱，用于个人中心展示和后续通知场景；不能为空。
     */
    @NotBlank(message = "email is required")
    @Email(message = "email format is invalid")
    @Size(max = 150, message = "email length must be less than 150")
    private String email;
}
