package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台角色权限授权保存请求 DTO
 *
 * <p>用于提交角色权限授权结果，承载角色主键和本次授予的权限主键集合。</p>
 */
@Data
public class SysRolePermissionGrantRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;

    private List<Long> permissionIds = Collections.emptyList();
}
