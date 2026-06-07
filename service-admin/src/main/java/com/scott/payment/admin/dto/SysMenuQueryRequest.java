package com.scott.payment.admin.dto;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuQueryRequest
 * @date : 2026-06-07 00:00
 * @description : 管理后台菜单查询请求
 * @status : create
 */
@Data
public class SysMenuQueryRequest {

    private String menuName;

    private String menuType;

    private Integer status;

    private Integer visible;
}
