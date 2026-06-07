package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRolePermissionGrantRequest
 * @date : 2026-06-07 00:00
 * @description : 角色权限授权保存请求
 * @status : create
 */
@Data
public class SysRolePermissionGrantRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;

    private List<Long> permissionIds = Collections.emptyList();
}
