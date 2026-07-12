package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 管理后台角色响应 DTO，位于 service-admin 接口传输层；用于角色列表、详情和用户授权弹窗展示角色基础信息。
 * @status : create
 */
@Data
public class SysRoleDTO {

    /**
     * 角色ID。
     */
    private Long roleId;

    /**
     * 角色编码。
     */
    private String roleCode;

    /**
     * 角色名称。
     */
    private String roleName;

    /**
     * 角色类型。
     */
    private String roleType;

    /**
     * 数据范围。
     */
    private String dataScope;

    /**
     * 角色说明。
     */
    private String description;

    /**
     * 状态：1启用，0停用。
     */
    private Integer status;

    /**
     * 排序号。
     */
    private Integer sortNo;

    /**
     * 菜单数量。
     */
    private Long menuCount;

    /**
     * 权限数量。
     */
    private Long permissionCount;

    /**
     * 当前操作人是否可将该角色授权给其他账号。
     */
    private Boolean assignable;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;
}
