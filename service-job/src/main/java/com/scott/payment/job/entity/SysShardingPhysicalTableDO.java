package com.scott.payment.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_sharding_physical_table")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysShardingPhysicalTableDO
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sys Sharding Physical Table DO 持久化模型，位于 调度任务服务，映射数据库记录字段，承载主键、业务标识、状态、时间和审计信息。
 * @status : create
 */
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
