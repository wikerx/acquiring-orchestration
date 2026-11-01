package com.scott.payment.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台角色权限授权响应 DTO
 *
 * <p>用于角色权限授权页面，返回角色信息、可授权权限清单和当前已勾选权限主键。</p>
 */
@Data
public class SysRolePermissionAuthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roleId;

    private String roleCode;

    private String roleName;

    private List<SysPermissionDTO> permissions = Collections.emptyList();

    private List<Long> checkedPermissionIds = Collections.emptyList();
}
