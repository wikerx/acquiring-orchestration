package com.scott.payment.job.dto.sharding;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateResult
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表precreate协作组件，位于 调度任务服务，封装该业务的本地校验、转换或运行时协作入口。
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

    /** 待评审的下一版 ShardingSphere 规则版本。 */
    private String candidateRuleVersion;

    /** 去除秘密后的候选规则 SHA-256 checksum。 */
    private String candidateRuleChecksum;

    /** 候选 actualDataNodes，仅包含已发布节点和本轮全表校验通过的季度。 */
    private List<String> verifiedPhysicalNodes = new ArrayList<>();

    /** 是否允许进入人工 Nacos 发布和滚动重启步骤。 */
    private Boolean publicationReady = Boolean.FALSE;

    /** 阻止新季度节点发布的原因。 */
    private List<String> publicationBlockers = new ArrayList<>();

    /** 下一步受控操作提示，不会自动发布 Nacos。 */
    private String nextAction;

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
