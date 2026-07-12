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
 * @email : scott_x@163.com
 * @description : 管理后台用户重置密码请求 DTO
 * @status : create
 *
 * <p>用于后台管理员重置指定用户密码，承载账号主键和新的明文密码入参。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountResetPasswordRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys User Account Reset Password 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysUserAccountResetPasswordRequest implements Serializable {

    /**
     * 系统管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @NotNull(message = "accountId")
    private Long accountId;

    /**
     * 系统管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    @NotBlank(message = "password")
    @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
    private String password;
}
