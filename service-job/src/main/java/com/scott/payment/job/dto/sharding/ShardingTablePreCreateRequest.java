package com.scott.payment.job.dto.sharding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表物理表预创建任务参数。
 *
 * <p>参数来源于任务中心 JSON 参数。{@code dryRun=true} 时只输出计划和检查结果，不执行 DDL。</p>
 */
@Data
public class ShardingTablePreCreateRequest {

    /**
     * 是否只预演。
     */
    private Boolean dryRun = Boolean.FALSE;

    /**
     * 是否处理当前季度。
     */
    private Boolean includeCurrentQuarter = Boolean.TRUE;

    /**
     * 是否处理下一季度。
     */
    private Boolean includeNextQuarter = Boolean.TRUE;

    /**
     * 指定逻辑表，为空表示处理全部 enabled=true 的分表配置。
     */
    private List<String> logicalTables = new ArrayList<>();

    /**
     * 目标表已存在时是否对比结构。
     */
    private Boolean compareSchemaIfExists = Boolean.TRUE;
}
