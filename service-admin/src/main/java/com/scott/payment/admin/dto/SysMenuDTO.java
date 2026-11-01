package com.scott.payment.admin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台菜单响应 DTO
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

    /**
     * 排序号，数值越小越优先展示或匹配。
     */
    private Integer sortNo;

    private Integer status;

    private List<SysMenuDTO> children = new ArrayList<>();
}
