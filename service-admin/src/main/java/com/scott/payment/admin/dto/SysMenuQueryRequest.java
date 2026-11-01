package com.scott.payment.admin.dto;

import lombok.Data;

/**
 * 管理后台菜单查询请求 DTO
 *
 * <p>用于菜单树筛选和管理列表检索，支持按菜单名称、类型、状态和可见性过滤。</p>
 */
@Data
public class SysMenuQueryRequest {

    private String menuName;

    private String menuType;

    private Integer status;

    /**
     * 请求中的可见，用于限定本次操作的输入和校验范围。
     */
    private Integer visible;
}
