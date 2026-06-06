package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 角色数据库实体
 * @status : create
 */
@Data
@TableName("sys_role")
public class SysRoleDO {

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
     * 角色编码。
     */
    private String roleCode;

    /**
     * 角色名称。
     */
    private String roleName;

    /**
     * 角色类型。
     */
    private String roleType;

    /**
     * 数据范围。
     */
    private String dataScope;

    /**
     * 角色说明。
     */
    private String description;

    /**
     * 状态。
     */
    private Integer status;

    /**
     * 排序号。
     */
    private Integer sortNo;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 创建人ID。
     */
    private Long createdBy;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 修改人ID。
     */
    private Long updatedBy;

    /**
     * 删除标识。
     */
    private Long deleted;
}
