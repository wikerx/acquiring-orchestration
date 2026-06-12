package com.scott.payment.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleAuthDTO
 * @date : 2026-06-07 00:00
 * @description : 后台用户角色授权响应对象
 * @status : create
 */
@Data
public class SysUserRoleAuthDTO {

    private Long accountId;

    private List<SysRoleDTO> roles;

    private List<Long> checkedRoleIds;
}
