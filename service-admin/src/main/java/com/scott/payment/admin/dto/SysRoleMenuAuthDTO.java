package com.scott.payment.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleMenuAuthDTO
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色菜单授权响应 DTO
 * @status : create
 *
 * <p>用于角色菜单授权页面，返回角色基础信息、可选菜单树和当前已勾选菜单主键。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleMenuAuthDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Role Menu Auth 数据传输对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysRoleMenuAuthDTO implements Serializable {

    /**
     * 系统管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long roleId;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String roleCode;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String roleName;

    private List<SysMenuDTO> menus = Collections.emptyList();

    private List<Long> checkedMenuIds = Collections.emptyList();
}
