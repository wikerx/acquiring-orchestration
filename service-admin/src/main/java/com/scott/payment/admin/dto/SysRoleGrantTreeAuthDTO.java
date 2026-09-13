package com.scott.payment.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台角色统一授权树响应，合并菜单与操作权限选择结果。
 */
@Data
public class SysRoleGrantTreeAuthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roleId;

    private String roleCode;

    private String roleName;

    private List<SysMenuDTO> menus = Collections.emptyList();

    private List<SysPermissionDTO> permissions = Collections.emptyList();

    private List<Long> checkedMenuIds = Collections.emptyList();

    private List<Long> checkedPermissionIds = Collections.emptyList();
}
