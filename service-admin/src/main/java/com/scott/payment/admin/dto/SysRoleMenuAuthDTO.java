package com.scott.payment.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台角色菜单授权响应 DTO
 *
 * <p>用于角色菜单授权页面，返回角色基础信息、可选菜单树和当前已勾选菜单主键。</p>
 */
@Data
public class SysRoleMenuAuthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roleId;

    private String roleCode;

    private String roleName;

    private List<SysMenuDTO> menus = Collections.emptyList();

    private List<Long> checkedMenuIds = Collections.emptyList();
}
