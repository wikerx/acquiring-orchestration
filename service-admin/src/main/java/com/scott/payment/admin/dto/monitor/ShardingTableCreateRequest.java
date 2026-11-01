package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表表写操作请求模型，用于运营后台，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
 */
@Data
public class ShardingTableCreateRequest {

    private Boolean includeCurrentQuarter = Boolean.TRUE;

    private Boolean includeNextQuarter = Boolean.TRUE;

    private List<String> logicalTables = new ArrayList<>();

    private Boolean compareSchemaIfExists = Boolean.TRUE;
}
