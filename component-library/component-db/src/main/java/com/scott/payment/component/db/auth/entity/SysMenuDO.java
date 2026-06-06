package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 菜单数据库实体
 * @status : create
 */
@Data
@TableName("sys_menu")
public class SysMenuDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统应用ID。
     */
    private Long appId;

    /**
     * 父级菜单ID。
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
     * 权限标识。
     */
    private String permissionCode;

    /**
     * 菜单图标。
     */
    private String icon;

    /**
     * 重定向地址。
     */
    private String redirect;

    /**
     * 是否显示。
     */
    private Integer visible;

    /**
     * 是否缓存页面。
     */
    private Integer keepAlive;

    /**
     * 是否外链。
     */
    private Integer externalLink;

    /**
     * 排序号。
     */
    private Integer sortNo;

    /**
     * 状态。
     */
    private Integer status;

    /**
     * 删除标识。
     */
    private Long deleted;
}
