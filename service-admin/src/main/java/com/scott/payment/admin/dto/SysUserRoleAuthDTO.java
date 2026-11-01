package com.scott.payment.admin.dto;

import lombok.Data;

import java.util.List;


/**
 * Admin 用户角色授权 DTO，返回账号当前角色和可分配角色集合。
 */
@Data
public class SysUserRoleAuthDTO {

    /**
     * 目标账号ID。
     */
    private Long accountId;

    /**
     * 当前操作人可见的角色清单；角色中的 assignable 标识是否可被当前操作人授权。
     */
    private List<SysRoleDTO> roles;

    /**
     * 目标账号当前已绑定角色ID集合。
     */
    private List<Long> checkedRoleIds;
}
