package com.scott.payment.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuDTO
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台菜单响应 DTO
 * @status : create
 *
 * <p>用于菜单树、权限装配和当前用户菜单返回，承载菜单基础信息与树形子节点。</p>
 */
@Data
public class SysMenuDTO {

    private Long menuId;

    private Long parentId;

    private String menuCode;

    private String menuName;

    private String menuType;

    private String routePath;

    private String componentPath;

    private String permissionCode;

    private String icon;

    private String redirect;

    private Integer visible;

    private Integer keepAlive;

    private Integer externalLink;

    private Integer sortNo;

    private Integer status;

    private List<SysMenuDTO> children = new ArrayList<>();
}
