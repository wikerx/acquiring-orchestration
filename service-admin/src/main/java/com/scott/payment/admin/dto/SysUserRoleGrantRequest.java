package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleGrantRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户角色授权请求 DTO
 * @status : create
 *
 * <p>用于提交用户角色授权结果，承载账号主键和本次授予的角色主键集合。</p>
 */
@Data
public class SysUserRoleGrantRequest {

    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    private List<Long> roleIds;
}
