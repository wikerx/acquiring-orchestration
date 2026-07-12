package com.scott.payment.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysShardingPhysicalTableDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Sharding Physical Table 数据库实体，位于 service-job 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_sharding_physical_table")
public class SysShardingPhysicalTableDO {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 逻辑表名。
     */
    private String logicalTable;

    /**
     * 模板表名。
     */
    private String templateTable;

    /**
     * 物理表名。
     */
    private String physicalTable;

    /**
     * 分表字段。
     */
    private String shardingColumn;

    /**
     * 分表策略。
     */
    private String strategy;

    /**
     * 年份。
     */
    private Integer year;

    /**
     * 季度：1-4。
     */
    private Integer quarter;

    /**
     * 季度后缀，例如 202602。
     */
    private String quarterSuffix;

    /**
     * 数据源名称。
     */
    private String dataSource;

    /**
     * 表状态。
     */
    private String tableStatus;

    /**
     * 是否自动创建。
     */
    private Integer autoCreated;

    /**
     * AUTO_INCREMENT 起始值。
     */
    private Long autoIncrementStart;

    /**
     * AUTO_INCREMENT 当前值。
     */
    private Long autoIncrementCurrent;

    /**
     * AUTO_INCREMENT 最大安全值。
     */
    private Long autoIncrementMax;

    /**
     * 结构校验状态。
     */
    private String schemaCheckStatus;

    /**
     * 最后检查时间。
     */
    private LocalDateTime lastCheckTime;

    /**
     * 物理表创建时间。
     */
    private LocalDateTime createdTime;

    /**
     * 失败原因。
     */
    private String errorMessage;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 记录更新时间。
     */
    private LocalDateTime updateTime;
}
