package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分表物理表登记数据对象。
 *
 * <p>仅用于管理后台查询治理表状态，不承载真实交易分表读写。</p>
 */
@Data
@TableName("sys_sharding_physical_table")
public class SysShardingPhysicalTableDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String logicalTable;

    private String templateTable;

    private String physicalTable;

    private String shardingColumn;

    private String strategy;

    private Integer year;

    private Integer quarter;

    private String quarterSuffix;

    private String dataSource;

    private String tableStatus;

    private Integer autoCreated;

    private Long autoIncrementStart;

    private Long autoIncrementCurrent;

    private Long autoIncrementMax;

    private String schemaCheckStatus;

    private LocalDateTime lastCheckTime;

    private LocalDateTime createdTime;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
