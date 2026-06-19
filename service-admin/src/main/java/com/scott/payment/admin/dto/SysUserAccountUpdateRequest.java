package com.scott.payment.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountUpdateRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户编辑请求 DTO
 * @status : create
 *
 * <p>用于更新后台用户基础资料和状态，不包含登录账号与密码等受限字段变更。</p>
 */
@Data
public class SysUserAccountUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "accountId")
    private Long accountId;

    @Size(max = 100, message = "realName length must be less than 100")
    private String realName;

    @Size(max = 30, message = "mobile length must be less than 30")
    private String mobile;

    @Email(message = "email format does not match")
    @Size(max = 150, message = "email length must be less than 150")
    private String email;

    private Integer status;
}
