package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分表物理表响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
 */
@Data
public class ShardingPhysicalTableResponse {

    @JsonSerialize(using = ToStringSerializer.class)
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

    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementStart;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementCurrent;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long autoIncrementMax;

    /**
     * 当前实例加载的交易分片规则版本。
     */
    private String ruleVersion;

    /**
     * 当前实例规则 checksum 前缀，不暴露配置秘密。
     */
    private String ruleChecksumPrefix;

    /**
     * 当前物理表季度是否已登记为 ShardingSphere 实际节点。
     */
    private Boolean nodeRegistered;

    private String schemaCheckStatus;

    private LocalDateTime lastCheckTime;

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
