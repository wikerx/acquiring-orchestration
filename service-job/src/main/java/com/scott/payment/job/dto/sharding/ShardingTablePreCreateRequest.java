package com.scott.payment.job.dto.sharding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表表pre写操作请求模型，位于 调度任务服务，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
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
