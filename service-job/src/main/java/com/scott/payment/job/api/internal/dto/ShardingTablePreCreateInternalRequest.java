package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateInternalRequest
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表precreateinternal请求模型，位于 调度任务服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
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
