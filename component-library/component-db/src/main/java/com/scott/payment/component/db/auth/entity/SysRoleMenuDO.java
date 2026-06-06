package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleMenuDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 角色菜单关联数据库实体
 * @status : create
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenuDO {

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
     * 角色ID。
     */
    private Long roleId;

    /**
     * 菜单ID。
     */
    private Long menuId;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 创建人ID。
     */
    private Long createdBy;

    /**
     * 删除标识。
     */
    private Long deleted;
}
