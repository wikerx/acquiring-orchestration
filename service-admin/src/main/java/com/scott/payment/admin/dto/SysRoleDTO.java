package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleDTO
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色响应 DTO
 * @status : create
 *
 * <p>用于角色列表、详情和授权页展示，承载角色基础信息及已分配菜单、权限数量摘要。</p>
 */
@Data
public class SysRoleDTO {

    private Long roleId;

    private String roleCode;

    private String roleName;

    private String roleType;

    private String dataScope;

    private String description;

    private Integer status;

    private Integer sortNo;

    private Long menuCount;

    private Long permissionCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
