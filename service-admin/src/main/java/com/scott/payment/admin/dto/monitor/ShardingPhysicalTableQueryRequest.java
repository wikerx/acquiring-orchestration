package com.scott.payment.admin.dto.monitor;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分表物理表查询条件模型，用于运营后台，承载筛选字段、时间范围和分页边界，不包含数据范围授权结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShardingPhysicalTableQueryRequest extends PageRequest {

    /**
     * 请求中的逻辑表，用于限定本次操作的输入和校验范围。
     */
    private String logicalTable;

    /**
     * 请求中的物理表，用于限定本次操作的输入和校验范围。
     */
    private String physicalTable;

    /**
     * 请求中的年份，用于限定本次操作的输入和校验范围。
     */
    private Integer year;

    /**
     * 请求中的季度，用于限定本次操作的输入和校验范围。
     */
    private Integer quarter;

    private String tableStatus;

    private String schemaCheckStatus;
}
