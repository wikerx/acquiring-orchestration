package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys分表物理表持久化模型，用于运营后台，映射数据库中的业务事实、状态、版本和审计字段，不作为外部接口模型。
 */
@Data
@TableName("sys_sharding_physical_table")
public class SysShardingPhysicalTableDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 持久化的逻辑表，用于还原当前记录的业务事实。
     */
    private String logicalTable;

    private String templateTable;

    /**
     * 持久化的物理表，用于还原当前记录的业务事实。
     */
    private String physicalTable;

    /**
     * 持久化的分表字段，用于还原当前记录的业务事实。
     */
    private String shardingColumn;

    /**
     * 持久化的策略，用于还原当前记录的业务事实。
     */
    private String strategy;

    /**
     * 持久化的年份，用于还原当前记录的业务事实。
     */
    private Integer year;

    /**
     * 持久化的季度，用于还原当前记录的业务事实。
     */
    private Integer quarter;

    /**
     * 持久化的{@code quarterSuffix}，用于还原当前记录的业务事实。
     */
    private String quarterSuffix;

    /**
     * 持久化的{@code dataSource}，用于还原当前记录的业务事实。
     */
    private String dataSource;

    private String tableStatus;

    /**
     * 持久化的自动创建，用于还原当前记录的业务事实。
     */
    private Integer autoCreated;

    /**
     * 持久化的自动自增开始，用于还原当前记录的业务事实。
     */
    private Long autoIncrementStart;

    /**
     * 持久化的自动自增当前，用于还原当前记录的业务事实。
     */
    private Long autoIncrementCurrent;

    /**
     * 持久化的自动自增最大，用于还原当前记录的业务事实。
     */
    private Long autoIncrementMax;

    private String schemaCheckStatus;

    /**
     * 持久化的{@code lastCheckTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime lastCheckTime;

    /**
     * 持久化的创建时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime createdTime;

    /**
     * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
     */
    private String errorMessage;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
