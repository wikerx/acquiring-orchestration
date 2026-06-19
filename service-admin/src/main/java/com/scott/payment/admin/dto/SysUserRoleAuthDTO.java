package com.scott.payment.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleAuthDTO
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户角色授权响应 DTO
 * @status : create
 *
 * <p>用于用户角色授权页面，返回目标账号、可选角色清单和当前已勾选角色主键。</p>
 */
@Data
public class SysUserRoleAuthDTO {

    private Long accountId;

    private List<SysRoleDTO> roles;

    private List<Long> checkedRoleIds;
}
