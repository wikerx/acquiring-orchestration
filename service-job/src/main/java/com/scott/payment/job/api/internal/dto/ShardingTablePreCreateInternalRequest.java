package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateInternalRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Pre Create Internal 请求对象，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
