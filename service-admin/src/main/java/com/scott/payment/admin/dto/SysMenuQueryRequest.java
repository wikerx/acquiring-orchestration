package com.scott.payment.admin.dto;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuQueryRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台菜单查询请求 DTO
 * @status : create
 *
 * <p>用于菜单树筛选和管理列表检索，支持按菜单名称、类型、状态和可见性过滤。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuQueryRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Menu Query 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysMenuQueryRequest {

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String menuName;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String menuType;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private Integer status;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer visible;
}
