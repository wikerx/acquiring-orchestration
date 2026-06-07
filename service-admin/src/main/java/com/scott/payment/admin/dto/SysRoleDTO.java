package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleDTO
 * @date : 2026-06-07 00:00
 * @description : 管理后台角色响应对象
 * @status : create
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
