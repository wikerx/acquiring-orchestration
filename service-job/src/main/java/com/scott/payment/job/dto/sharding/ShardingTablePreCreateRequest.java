package com.scott.payment.job.dto.sharding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateRequest
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Table Pre Create Request 传输模型，位于 调度任务服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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
