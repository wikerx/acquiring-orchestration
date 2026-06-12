package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleGrantRequest
 * @date : 2026-06-07 00:00
 * @description : 后台用户角色授权请求对象
 * @status : create
 */
@Data
public class SysUserRoleGrantRequest {

    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    private List<Long> roleIds;
}
