package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表规则响应模型。
 *
 * <p>来源于 Nacos 分表配置，用于后台查看逻辑表、模板表、物理表范围和维护策略。</p>
 */
@Data
public class ShardingRuleResponse {

    private String ruleKey;

    private String logicalTable;

    private String templateTable;

    private Boolean enabled;

    private String idColumn;

    private String shardingColumn;

    private String actualDataSource;

    private String description;

    private Integer startYear;

    private Integer startQuarter;

    private Integer endYear;

    private Integer endQuarter;

    private String tableNameFormat;

    private String currentPhysicalTable;

    private String nextPhysicalTable;

    private Integer physicalTableCount;

    private List<String> physicalTables = new ArrayList<>();
}
