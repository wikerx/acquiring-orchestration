package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleDTO
 * @date : 2026-06-07 08:26
 * @email : scott_x@163.com
 * @description : Sys Role DTO 传输模型，位于 运营后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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
