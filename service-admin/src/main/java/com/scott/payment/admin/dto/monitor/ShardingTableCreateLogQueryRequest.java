package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分表建表任务日志查询请求。
 *
 * <p>支持按批次号、触发方式、预演标识和运行状态筛选分表建表批次。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShardingTableCreateLogQueryRequest extends PageRequest {

    private String batchNo;

    private String triggerType;

    private Integer dryRun;

    private String runStatus;
}
