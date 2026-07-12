package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAppDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 系统应用数据库实体
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAppDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys App 数据库实体，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_app")
public class SysAppDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统编码，例如 ADMIN、MERCHANT。
     */
    private String appCode;

    /**
     * 系统名称。
     */
    private String appName;

    /**
     * 系统类型。
     */
    private String appType;

    /**
     * 系统访问域名或地址。
     */
    private String domainUrl;

    /**
     * 系统说明。
     */
    private String description;

    /**
     * 状态：0停用，1启用。
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
     * 删除标识：0未删除，大于0为删除记录ID。
     */
    private Long deleted;
}
