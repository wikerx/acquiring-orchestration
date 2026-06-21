package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表物理表创建请求。
 *
 * <p>管理后台使用该请求触发预演或立即建表，实际 DDL 由 service-job 内部接口统一处理。</p>
 */
@Data
public class ShardingTableCreateRequest {

    private Boolean includeCurrentQuarter = Boolean.TRUE;

    private Boolean includeNextQuarter = Boolean.TRUE;

    private List<String> logicalTables = new ArrayList<>();

    private Boolean compareSchemaIfExists = Boolean.TRUE;
}
