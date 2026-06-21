package com.scott.payment.admin.client.job.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 调用 service-job 分表预建表接口的内部请求。
 */
@Data
public class ShardingTablePreCreateRemoteRequest {

    private Boolean dryRun;

    private Boolean includeCurrentQuarter = Boolean.TRUE;

    private Boolean includeNextQuarter = Boolean.TRUE;

    private List<String> logicalTables = new ArrayList<>();

    private Boolean compareSchemaIfExists = Boolean.TRUE;

    private String operatorId;

    private String operatorName;
}
