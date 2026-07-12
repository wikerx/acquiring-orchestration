package com.scott.payment.job.dto.sharding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateResult
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Pre Create Result，位于 service-job 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ShardingTablePreCreateResult {

    /**
     * 是否只预演。
     */
    private Boolean dryRun;

    /**
     * 使用的数据库时区。
     */
    private String timezone;

    /**
     * 分表策略。
     */
    private String strategy;

    /**
     * 当前季度。
     */
    private String currentQuarter;

    /**
     * 目标季度。
     */
    private List<String> targetQuarters = new ArrayList<>();

    /**
     * 已创建表。
     */
    private List<String> createdTables = new ArrayList<>();

    /**
     * 已跳过表。
     */
    private List<String> skippedTables = new ArrayList<>();

    /**
     * 失败表。
     */
    private List<String> failedTables = new ArrayList<>();

    /**
     * 结构不一致表。
     */
    private List<String> schemaMismatchTables = new ArrayList<>();

    /**
     * 告警信息。
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 单表处理明细。
     */
    private List<ShardingTablePreCreateTableResult> tableResults = new ArrayList<>();
}
