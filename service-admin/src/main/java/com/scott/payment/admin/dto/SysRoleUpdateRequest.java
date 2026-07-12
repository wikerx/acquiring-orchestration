package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleUpdateRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色编辑请求 DTO
 * @status : create
 *
 * <p>用于更新角色展示信息、数据范围、状态和排序等可变配置。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleUpdateRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Role Update 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysRoleUpdateRequest implements Serializable {

    /**
     * 系统管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @NotNull(message = "roleId")
    private Long roleId;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Size(max = 100, message = "roleName length must be less than 100")
    private String roleName;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String dataScope;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Size(max = 500, message = "description length must be less than 500")
    private String description;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private Integer status;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private Integer sortNo;
}
