package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分表创建日志分页查询条件，支持按批次、触发类型和执行状态筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShardingTableCreateLogQueryRequest extends PageRequest {

    /**
     * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
     */
    private String batchNo;

    /**
     * 触发类型，用于区分手工触发、定时调度、失败重试或系统补偿执行。
     */
    private String triggerType;

    private Integer dryRun;

    private String runStatus;
}
