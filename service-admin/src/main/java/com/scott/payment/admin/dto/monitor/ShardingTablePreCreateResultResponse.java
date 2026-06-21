package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分表物理表预创建结果响应。
 *
 * <p>该模型承接 service-job 内部接口返回的 JSON 结构，避免 service-admin 直接依赖 job 模块 DTO。</p>
 */
@Data
public class ShardingTablePreCreateResultResponse {

    private Boolean dryRun;

    private String timezone;

    private String strategy;

    private String currentQuarter;

    private List<String> targetQuarters = new ArrayList<>();

    private List<String> createdTables = new ArrayList<>();

    private List<String> skippedTables = new ArrayList<>();

    private List<String> failedTables = new ArrayList<>();

    private List<String> schemaMismatchTables = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    private List<ShardingTablePreCreateTableResultResponse> tableResults = new ArrayList<>();
}
