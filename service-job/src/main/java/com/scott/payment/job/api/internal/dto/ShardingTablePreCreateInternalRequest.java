package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表物理表预创建内部请求。
 *
 * <p>该请求只允许管理后台通过内部接口触发，用于预演或立即创建缺失的测试分表物理表。</p>
 */
@Data
public class ShardingTablePreCreateInternalRequest {

    /**
     * 是否只预演，不执行 DDL。
     */
    private Boolean dryRun;

    /**
     * 是否包含当前季度。
     */
    private Boolean includeCurrentQuarter = Boolean.TRUE;

    /**
     * 是否包含下一季度。
     */
    private Boolean includeNextQuarter = Boolean.TRUE;

    /**
     * 指定逻辑表；为空时处理所有启用的逻辑表。
     */
    private List<String> logicalTables = new ArrayList<>();

    /**
     * 目标物理表已存在时是否对比模板表结构。
     */
    private Boolean compareSchemaIfExists = Boolean.TRUE;

    /**
     * 管理后台操作人 ID。
     */
    private String operatorId;

    /**
     * 管理后台操作人名称。
     */
    private String operatorName;
}
