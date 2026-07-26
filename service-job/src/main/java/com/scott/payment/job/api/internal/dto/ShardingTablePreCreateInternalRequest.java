package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateInternalRequest
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTablePreCreateInternalRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
