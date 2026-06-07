package com.scott.payment.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleMenuAuthDTO
 * @date : 2026-06-07 00:00
 * @description : 角色菜单授权响应
 * @status : create
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
