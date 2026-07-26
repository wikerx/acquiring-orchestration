package com.scott.payment.job.dto.sharding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateResult
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTablePreCreateResult 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
