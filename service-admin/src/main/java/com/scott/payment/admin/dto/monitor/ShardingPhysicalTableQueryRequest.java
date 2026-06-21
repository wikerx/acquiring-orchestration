package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分表物理表查询请求。
 *
 * <p>支持按逻辑表、物理表、季度、状态和结构校验结果检索治理表登记记录。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShardingPhysicalTableQueryRequest extends PageRequest {

    private String logicalTable;

    private String physicalTable;

    private Integer year;

    private Integer quarter;

    private String tableStatus;

    private String schemaCheckStatus;
}
