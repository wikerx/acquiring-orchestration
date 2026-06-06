package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysPermissionDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 权限资源数据库实体
 * @status : create
 */
@Data
@TableName("sys_permission")
public class SysPermissionDO {

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
     * 归属菜单ID。
     */
    private Long menuId;

    /**
     * 权限编码。
     */
    private String permissionCode;

    /**
     * 权限名称。
     */
    private String permissionName;

    /**
     * 权限类型。
     */
    private String permissionType;

    /**
     * 接口请求方法。
     */
    private String resourceMethod;

    /**
     * 接口资源路径。
     */
    private String resourcePath;

    /**
     * 状态。
     */
    private Integer status;

    /**
     * 删除标识。
     */
    private Long deleted;
}
