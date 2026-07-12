package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuUpdateRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台菜单编辑请求 DTO
 * @status : create
 *
 * <p>用于编辑已有菜单配置，承载菜单层级、路由、权限编码和展示属性变更。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuUpdateRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Menu Update 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysMenuUpdateRequest {

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @NotNull(message = "菜单ID不能为空")
    private Long menuId;

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @NotNull(message = "父级菜单ID不能为空")
    private Long parentId;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String routePath;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String componentPath;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String permissionCode;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String icon;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String redirect;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer visible;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer keepAlive;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer externalLink;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private Integer sortNo;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private Integer status;
}
