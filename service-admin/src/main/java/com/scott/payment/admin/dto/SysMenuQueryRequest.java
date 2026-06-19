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
@Data
public class SysMenuQueryRequest {

    private String menuName;

    private String menuType;

    private Integer status;

    private Integer visible;
}
