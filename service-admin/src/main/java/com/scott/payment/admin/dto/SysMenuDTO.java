package com.scott.payment.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuDTO
 * @date : 2026-06-07 00:00
 * @description : 管理后台菜单响应对象
 * @status : create
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
