package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysPostDO
 * @date : 2026-06-12 20:00
 * @email : scott_x@163.com
 * @description : 岗位数据库实体，对应 sys_post 表
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysPostDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Post 数据库实体，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_post")
public class SysPostDO {

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
     * 岗位编码。
     */
    private String postCode;

    /**
     * 岗位名称。
     */
    private String postName;

    /**
     * 显示排序。
     */
    private Integer sortNo;

    /**
     * 状态：0 停用，1 启用。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

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
