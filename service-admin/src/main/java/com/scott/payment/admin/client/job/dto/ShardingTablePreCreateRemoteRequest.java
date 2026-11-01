package com.scott.payment.admin.client.job.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分片表预创建内部请求模型，用于运营后台，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
 */
@Data
public class ShardingTablePreCreateRemoteRequest {

    private Boolean dryRun;

    private Boolean includeCurrentQuarter = Boolean.TRUE;

    private Boolean includeNextQuarter = Boolean.TRUE;

    private List<String> logicalTables = new ArrayList<>();

    private Boolean compareSchemaIfExists = Boolean.TRUE;

    /**
     * 执行本次管理操作的可信登录账号 ID，用于操作审计。
     */
    private String operatorId;

    /**
     * 执行本次管理操作时的账号显示名称快照，用于操作审计。
     */
    private String operatorName;
}
