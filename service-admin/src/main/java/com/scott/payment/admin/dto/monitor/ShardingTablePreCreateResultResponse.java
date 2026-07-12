package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateResultResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Sharding Table Pre Create Result 响应对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ShardingTablePreCreateResultResponse {

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Boolean dryRun;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private String timezone;

    /**
     * 监控治理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    private String strategy;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String currentQuarter;

    private List<String> targetQuarters = new ArrayList<>();

    private List<String> createdTables = new ArrayList<>();

    private List<String> skippedTables = new ArrayList<>();

    private List<String> failedTables = new ArrayList<>();

    private List<String> schemaMismatchTables = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    private List<ShardingTablePreCreateTableResultResponse> tableResults = new ArrayList<>();
}
