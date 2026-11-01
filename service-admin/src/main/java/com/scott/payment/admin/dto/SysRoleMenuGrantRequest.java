package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台角色菜单授权保存请求 DTO
 *
 * <p>用于提交角色菜单授权结果，承载角色主键和本次授予的菜单主键集合。</p>
 */
@Data
public class SysRoleMenuGrantRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;

    private List<Long> menuIds = Collections.emptyList();
}
