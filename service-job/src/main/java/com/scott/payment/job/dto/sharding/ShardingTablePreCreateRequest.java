package com.scott.payment.job.dto.sharding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Pre Create 请求对象，位于 service-job 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
