package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDeptDO
 * @date : 2026-06-12 20:00
 * @email : scott_x@163.com
 * @description : 部门数据库实体，对应 sys_dept 表
 * @status : create
 */
@Data
@TableName("sys_dept")
public class SysDeptDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统应用 ID。
     */
    private Long appId;

    /**
     * 父部门 ID，根部门为 0。
     */
    private Long parentId;

    /**
     * 部门名称。
     */
    private String deptName;

    /**
     * 显示排序。
     */
    private Integer sortNo;

    /**
     * 负责人。
     */
    private String leader;

    /**
     * 联系电话。
     */
    private String phone;

    /**
     * 邮箱。
     */
    private String email;

    /**
     * 状态：0 停用，1 启用。
     */
    private Integer status;

    /**
     * 删除标识：0 未删除，大于 0 为删除记录 ID。
     */
    private Long deleted;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
