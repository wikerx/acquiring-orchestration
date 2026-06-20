package com.scott.payment.component.db.auth.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthMenuDTO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 前端菜单树节点
 * @status : create
 */
@Data
public class AuthMenuDTO implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID。
     */
    private Long id;

    /**
     * 父菜单ID。
     */
    private Long parentId;

    /**
     * 菜单编码。
     */
    private String menuCode;

    /**
     * 菜单名称。
     */
    private String menuName;

    /**
     * 菜单类型。
     */
    private String menuType;

    /**
     * 前端路由路径。
     */
    private String routePath;

    /**
     * 前端组件路径。
     */
    private String componentPath;

    /**
     * 前端按钮权限标识。
     */
    private String permissionCode;

    /**
     * 菜单图标。
     */
    private String icon;

    /**
     * 排序号。
     */
    private Integer sortNo;

    /**
     * 是否为新窗口外链。
     * 0 表示系统内承载，1 表示新窗口打开。
     */
    private Integer externalLink;

    /**
     * 子菜单列表。
     */
    private List<AuthMenuDTO> children = new ArrayList<>();
}
