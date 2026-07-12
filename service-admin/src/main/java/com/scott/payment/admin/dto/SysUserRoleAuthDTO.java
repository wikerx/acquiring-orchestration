package com.scott.payment.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleAuthDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 后台用户角色授权响应 DTO，位于 service-admin 接口传输层；返回目标账号、角色清单和当前已绑定角色。
 * @status : create
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
