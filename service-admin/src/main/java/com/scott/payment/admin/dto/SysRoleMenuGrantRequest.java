package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleMenuGrantRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色菜单授权保存请求 DTO
 * @status : create
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
