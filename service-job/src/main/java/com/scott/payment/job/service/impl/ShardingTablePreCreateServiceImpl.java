package com.scott.payment.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.sharding.TransactionShardingGovernanceProperties;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementRange;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementValueCalculator;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.ShardingTableDdlService;
import com.scott.payment.component.db.sharding.ShardingTableInspectionResult;
import com.scott.payment.component.db.sharding.ShardingTableSchemaInspector;
import com.scott.payment.component.db.sharding.TransactionShardingGovernanceProperties;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.db.sharding.TransactionShardingRuleChecksum;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateTableResult;
import com.scott.payment.job.entity.SysShardingPhysicalTableDO;
import com.scott.payment.job.entity.SysShardingTableCreateLogDO;
import com.scott.payment.job.mapper.SysShardingPhysicalTableMapper;
import com.scott.payment.job.mapper.SysShardingTableCreateLogMapper;
import com.scott.payment.job.service.ShardingTablePreCreateService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateServiceImpl
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表precreate服务实现，位于 调度任务服务，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。
 * @status : create
 */
@Service
public class ShardingTablePreCreateServiceImpl implements ShardingTablePreCreateService {

    private final TransactionShardingGovernanceProperties shardingProperties;

    /** 当前已发布交易逻辑规则，用于生成仅增加已验证季度的候选版本。 */
    private final TransactionShardingProperties transactionShardingProperties;
    private final ShardingQuarterResolver quarterResolver;
    private final ShardingPhysicalTableNameResolver tableNameResolver;
    private final ShardingAutoIncrementValueCalculator autoIncrementValueCalculator;
    private final ShardingTableSchemaInspector schemaInspector;
    private final ShardingTableDdlService ddlService;
    private final SysShardingPhysicalTableMapper physicalTableMapper;
    private final SysShardingTableCreateLogMapper createLogMapper;

    /**
     * 创建分表物理表预创建服务。
     *
     * @param shardingProperties           分表配置
     * @param quarterResolver              季度解析器
     * @param tableNameResolver            表名解析器
     * @param autoIncrementValueCalculator AUTO_INCREMENT 计算器
     * @param schemaInspector              表结构检查器
     * @param ddlService                   DDL 服务
     * @param physicalTableMapper          物理表登记 Mapper
     * @param createLogMapper              建表任务日志 Mapper
     */
    public ShardingTablePreCreateServiceImpl(TransactionShardingGovernanceProperties shardingProperties,
                                             TransactionShardingProperties transactionShardingProperties,
                                             ShardingQuarterResolver quarterResolver,
                                             ShardingPhysicalTableNameResolver tableNameResolver,
                                             ShardingAutoIncrementValueCalculator autoIncrementValueCalculator,
                                             ShardingTableSchemaInspector schemaInspector,
                                             ShardingTableDdlService ddlService,
                                             SysShardingPhysicalTableMapper physicalTableMapper,
                                             SysShardingTableCreateLogMapper createLogMapper) {
        this.shardingProperties = shardingProperties;
        this.transactionShardingProperties = transactionShardingProperties;
        this.quarterResolver = quarterResolver;
        this.tableNameResolver = tableNameResolver;
        this.autoIncrementValueCalculator = autoIncrementValueCalculator;
        this.schemaInspector = schemaInspector;
        this.ddlService = ddlService;
        this.physicalTableMapper = physicalTableMapper;
        this.createLogMapper = createLogMapper;
    }

    /**
     * 执行分表物理表预创建。
     *
     * @param request 任务参数
     * @param context 任务执行上下文
     * @return 预创建结果
     */
    @Override
    public ShardingTablePreCreateResult preCreate(ShardingTablePreCreateRequest request, JobExecuteContext context) {
        ShardingTablePreCreateRequest safeRequest = request == null ? new ShardingTablePreCreateRequest() : request;
        Instant start = Instant.now();
        ShardingTablePreCreateResult result = buildBaseResult(safeRequest);
        String batchNo = context == null || !StringUtils.hasText(context.getRunId())
                ? "manual-" + System.currentTimeMillis()
                : context.getRunId();
        try {
            validateGovernanceConfiguration(safeRequest);
            List<Map.Entry<String, TransactionShardingGovernanceProperties.TableRule>> rules = enabledRules(safeRequest);
            List<ShardingQuarter> targetQuarters = targetQuarters(safeRequest);
            result.setTargetQuarters(targetQuarters.stream().map(ShardingQuarter::displayName).toList());
            addExpiryWarnings(result, rules);
            for (Map.Entry<String, TransactionShardingGovernanceProperties.TableRule> entry : rules) {
                processRule(entry.getValue(), targetQuarters, safeRequest, result);
            }
            completeRuleCandidate(result, rules, targetQuarters);
            saveCreateLog(batchNo, safeRequest, context, result, start, null);
            return result;
        } catch (RuntimeException exception) {
            String failure = safeFailure(exception);
            result.getWarnings().add(failure);
            saveCreateLog(batchNo, safeRequest, context, result, start, failure);
            throw exception;
        }
    }

    /**
     * 校验治理配置只包含并完整覆盖当前正式交易逻辑表。
     */
    private void validateGovernanceConfiguration(ShardingTablePreCreateRequest request) {
        Set<String> publishedTables = new LinkedHashSet<>(transactionShardingProperties.getLogicTables());
        if ((!transactionShardingProperties.usesLegacyLogicTableTopology()
                && !transactionShardingProperties.usesFormalLogicTableTopology())
                || publishedTables.size() != transactionShardingProperties.getLogicTables().size()) {
            throw new IllegalStateException(
                    "transaction sharding published baseline must be the complete legacy 25-table or formal 28-table topology");
        }
        Set<String> expectedTables = new LinkedHashSet<>(TransactionShardingProperties.defaultLogicTables());
        if (expectedTables.size() != TransactionShardingProperties.FORMAL_LOGIC_TABLE_COUNT) {
            throw new IllegalStateException("transaction sharding logic table baseline must contain exactly "
                    + TransactionShardingProperties.FORMAL_LOGIC_TABLE_COUNT + " tables");
        }
        if (!TransactionShardingProperties.REQUIRED_ZONE_ID.equals(shardingProperties.getDatabaseTimezone())) {
            throw new IllegalStateException("transaction governance database timezone must be Asia/Shanghai");
        }
        if (!"quarter".equalsIgnoreCase(shardingProperties.getStrategy())) {
            throw new IllegalStateException("transaction governance strategy must be quarter");
        }
        if (shardingProperties.getTables().size() != expectedTables.size()) {
            throw new IllegalStateException("transaction governance rules must contain exactly "
                    + TransactionShardingProperties.FORMAL_LOGIC_TABLE_COUNT + " formal tables");
        }
        Set<String> configuredTables = new LinkedHashSet<>();
        for (TransactionShardingGovernanceProperties.TableRule rule : shardingProperties.getTables().values()) {
            if (rule == null || !Boolean.TRUE.equals(rule.getEnabled()) || !StringUtils.hasText(rule.getLogicalTable())) {
                throw new IllegalStateException("all transaction governance table rules must be enabled and named");
            }
            if (!configuredTables.add(rule.getLogicalTable())) {
                throw new IllegalStateException("duplicate transaction governance logic table rule");
            }
            if (!TransactionShardingProperties.REQUIRED_SHARDING_COLUMN.equals(rule.getShardingColumn())) {
                throw new IllegalStateException("transaction governance rule sharding column must be transaction_date_time");
            }
            if (!"master".equals(rule.getActualDataSource())) {
                throw new IllegalStateException("transaction governance DDL data source must be master");
            }
        }
        if (!configuredTables.equals(expectedTables)) {
            throw new IllegalStateException("transaction governance rules differ from the formal logic tables");
        }
        Set<String> requestedTables = new LinkedHashSet<>(request.getLogicalTables() == null
                ? List.of() : request.getLogicalTables());
        if (!expectedTables.containsAll(requestedTables)) {
            throw new IllegalStateException("pre-create request contains an unknown transaction logic table");
        }
    }

    /**
     * 创建分表预建任务的基础结果快照。
     *
     * @param request 任务请求
     * @return 包含演练标识、数据库时区、分表策略和当前季度的结果
     */
    private ShardingTablePreCreateResult buildBaseResult(ShardingTablePreCreateRequest request) {
        ShardingTablePreCreateResult result = new ShardingTablePreCreateResult();
        ShardingQuarter currentQuarter = quarterResolver.currentQuarter(shardingProperties);
        result.setDryRun(Boolean.TRUE.equals(request.getDryRun()));
        result.setTimezone(shardingProperties.getDatabaseTimezone());
        result.setStrategy(shardingProperties.getStrategy());
        result.setCurrentQuarter(currentQuarter.displayName());
        return result;
    }

    /**
     * 筛选当前启用且被请求选中的逻辑分表规则。
     * <p>
     * 请求未指定逻辑表时返回全部启用规则；同时兼容配置 Map 键和规则内 logicalTable 名称。
     * </p>
     *
     * @param request 任务请求
     * @return 保持配置顺序的启用规则列表
     */
    private List<Map.Entry<String, TransactionShardingGovernanceProperties.TableRule>> enabledRules(ShardingTablePreCreateRequest request) {
        Set<String> logicalTables = new LinkedHashSet<>(request.getLogicalTables() == null ? List.of() : request.getLogicalTables());
        return shardingProperties.getTables().entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue().getEnabled()))
                .filter(entry -> logicalTables.isEmpty()
                        || logicalTables.contains(entry.getKey())
                        || logicalTables.contains(entry.getValue().getLogicalTable()))
                .toList();
    }

    private List<ShardingQuarter> targetQuarters(ShardingTablePreCreateRequest request) {
        ShardingQuarter current = quarterResolver.currentQuarter(shardingProperties);
        Set<ShardingQuarter> quarters = new LinkedHashSet<>();
        if (Boolean.TRUE.equals(request.getIncludeCurrentQuarter())) {
            quarters.add(current);
        }
        if (Boolean.TRUE.equals(request.getIncludeNextQuarter())) {
            quarters.add(current.next());
        }
        return new ArrayList<>(quarters);
    }

    private void processRule(TransactionShardingGovernanceProperties.TableRule rule,
                             List<ShardingQuarter> targetQuarters,
                             ShardingTablePreCreateRequest request,
                             ShardingTablePreCreateResult result) {
        for (ShardingQuarter quarter : targetQuarters) {
            if (!quarterResolver.inRange(rule, quarter)) {
                result.getWarnings().add(rule.getLogicalTable() + " " + quarter.displayName() + " is outside configured range");
                continue;
            }
            ShardingTablePreCreateTableResult tableResult = processTable(rule, quarter, request);
            result.getTableResults().add(tableResult);
            collectSummary(result, tableResult);
        }
    }

    private ShardingTablePreCreateTableResult processTable(TransactionShardingGovernanceProperties.TableRule rule,
                                                           ShardingQuarter quarter,
                                                           ShardingTablePreCreateRequest request) {
        ShardingTablePreCreateTableResult tableResult = new ShardingTablePreCreateTableResult();
        String physicalTable = tableNameResolver.physicalTableName(rule, quarter);
        ShardingAutoIncrementRange range = autoIncrementValueCalculator.calculate(shardingProperties, quarter);
        tableResult.setLogicalTable(rule.getLogicalTable());
        tableResult.setTemplateTable(tableNameResolver.templateTableName(rule));
        tableResult.setPhysicalTable(physicalTable);
        tableResult.setTargetQuarter(quarter.displayName());
        tableResult.setAutoIncrementStart(range.startValue());
        tableResult.setAutoIncrementMax(range.maxValue());
        try {
            boolean exists = schemaInspector.tableExists(physicalTable);
            ShardingTableInspectionResult inspection;
            if (Boolean.TRUE.equals(request.getDryRun())) {
                inspection = exists
                        ? schemaInspector.inspectPhysicalTable(rule, physicalTable)
                        : schemaInspector.inspectTemplate(rule);
                tableResult.setStatus(exists ? "SKIPPED" : "DRY_RUN");
                tableResult.setMessage(exists ? "physical table already exists" : "dry run only");
            } else {
                inspection = ddlService.createPhysicalTableIfAbsent(shardingProperties, rule, quarter);
                tableResult.setStatus(exists ? "SKIPPED" : "CREATED");
                tableResult.setMessage(exists ? "physical table already exists" : "physical table created");
            }
            tableResult.setSchemaCheckStatus(inspection.getSchemaCheckStatus());
            tableResult.setShardingTimeCheckStatus(
                    inspection.isShardingColumnPrecisionMatched() ? "MATCHED" : "MISMATCHED");
            tableResult.setCharsetCheckStatus(inspection.isCharsetMatched() ? "MATCHED" : "MISMATCHED");
            tableResult.setAutoIncrementCurrent(inspection.getAutoIncrementCurrent());
            tableResult.setAutoIncrementCheckStatus(autoIncrementCheckStatus(
                    exists || !Boolean.TRUE.equals(request.getDryRun()), inspection.getAutoIncrementCurrent(), range));
            if (!Boolean.TRUE.equals(request.getDryRun()) || exists) {
                if (!Objects.equals("MATCHED", inspection.getSchemaCheckStatus())
                        || !Objects.equals("MATCHED", tableResult.getAutoIncrementCheckStatus())) {
                    tableResult.setStatus("MISMATCHED");
                    tableResult.setMessage(inspection.getMessage() == null
                            ? "physical table schema or auto increment range is invalid"
                            : inspection.getMessage());
                }
            } else if (!Objects.equals("MATCHED", inspection.getSchemaCheckStatus())) {
                tableResult.setStatus("MISMATCHED");
                tableResult.setMessage(inspection.getMessage());
            }
            savePhysicalTable(rule, quarter, tableResult, exists, null);
            return tableResult;
        } catch (RuntimeException exception) {
            tableResult.setStatus("FAILED");
            tableResult.setSchemaCheckStatus("FAILED");
            tableResult.setShardingTimeCheckStatus("FAILED");
            tableResult.setCharsetCheckStatus("FAILED");
            tableResult.setAutoIncrementCheckStatus("FAILED");
            tableResult.setMessage(safeFailure(exception));
            savePhysicalTable(rule, quarter, tableResult, false, safeFailure(exception));
            return tableResult;
        }
    }

    /**
     * 校验物理表 AUTO_INCREMENT 当前值是否仍位于该季度预留号段。
     *
     * @return PLANNED、MATCHED 或 MISMATCHED
     */
    private String autoIncrementCheckStatus(boolean physicalTableExists,
                                            Long currentValue,
                                            ShardingAutoIncrementRange range) {
        if (!physicalTableExists) {
            return "PLANNED";
        }
        if (currentValue == null || currentValue < range.startValue() || currentValue > range.maxValue()) {
            return "MISMATCHED";
        }
        return "MATCHED";
    }

    /** 在规则结束前按滚动季度窗口发出续期告警，不恢复历史查询跨度限制。 */
    private void addExpiryWarnings(
            ShardingTablePreCreateResult result,
            List<Map.Entry<String, TransactionShardingGovernanceProperties.TableRule>> rules) {
        int warningQuarters = Math.max(shardingProperties.getExpiryWarningQuarters(), 1);
        ShardingQuarter warningBoundary = quarterResolver.currentQuarter(shardingProperties);
        for (int i = 0; i < warningQuarters; i++) {
            warningBoundary = warningBoundary.next();
        }
        for (Map.Entry<String, TransactionShardingGovernanceProperties.TableRule> entry : rules) {
            if (quarterResolver.endQuarter(entry.getValue()).compareTo(warningBoundary) <= 0) {
                result.getWarnings().add("governance range renewal required for " + entry.getValue().getLogicalTable());
            }
        }
    }

    /**
     * 仅当一个季度的全部正式表均通过结构、时区字段、字符集和号段校验时，才加入候选节点。
     */
    private void completeRuleCandidate(
            ShardingTablePreCreateResult result,
            List<Map.Entry<String, TransactionShardingGovernanceProperties.TableRule>> rules,
            List<ShardingQuarter> targetQuarters) {
        Set<String> expectedTables = new LinkedHashSet<>(TransactionShardingProperties.defaultLogicTables());
        Set<String> selectedTables = new LinkedHashSet<>();
        rules.forEach(entry -> selectedTables.add(entry.getValue().getLogicalTable()));
        if (!selectedTables.equals(expectedTables)) {
            result.getPublicationBlockers().add("candidate publication requires all formal logic tables");
        }
        Set<String> verifiedNodes = transactionShardingProperties.usesFormalLogicTableTopology()
                ? new LinkedHashSet<>(transactionShardingProperties.getPhysicalNodes())
                : new LinkedHashSet<>();
        Set<String> targetNodeSuffixes = targetQuarters.stream()
                .map(ShardingQuarter::suffix)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (transactionShardingProperties.usesLegacyLogicTableTopology()) {
            transactionShardingProperties.getPhysicalNodes().stream()
                    .filter(node -> !targetNodeSuffixes.contains(node))
                    .forEach(node -> result.getPublicationBlockers().add(
                            "legacy node " + node + " must be revalidated against all 28 formal tables"));
        }
        for (ShardingQuarter quarter : targetQuarters) {
            List<ShardingTablePreCreateTableResult> quarterResults = result.getTableResults().stream()
                    .filter(item -> quarter.displayName().equals(item.getTargetQuarter()))
                    .toList();
            Set<String> quarterTables = new LinkedHashSet<>();
            quarterResults.forEach(item -> quarterTables.add(item.getLogicalTable()));
            boolean ready = quarterTables.equals(expectedTables)
                    && quarterResults.stream().allMatch(this::isPublicationReadyTable);
            if (ready) {
                verifiedNodes.add(quarter.suffix());
            } else {
                result.getPublicationBlockers().add(
                        "quarter " + quarter.displayName() + " has missing or unverified physical tables");
            }
        }
        result.setVerifiedPhysicalNodes(new ArrayList<>(verifiedNodes));
        String versionSuffix = verifiedNodes.isEmpty() ? "none"
                : new ArrayList<>(verifiedNodes).get(verifiedNodes.size() - 1);
        String currentVersion = StringUtils.hasText(transactionShardingProperties.getRuleVersion())
                ? transactionShardingProperties.getRuleVersion() : "unpublished";
        String candidateVersion = currentVersion + "-candidate-" + versionSuffix;
        TransactionShardingProperties candidate = copyTransactionRule(candidateVersion, verifiedNodes);
        result.setCandidateRuleVersion(candidateVersion);
        result.setCandidateRuleChecksum(TransactionShardingRuleChecksum.calculate(candidate));
        result.setPublicationReady(result.getPublicationBlockers().isEmpty());
        result.setNextAction(Boolean.TRUE.equals(result.getPublicationReady())
                ? "REVIEW_AND_PUBLISH_VERSIONED_NACOS_THEN_ROLLING_RESTART"
                : "FIX_BLOCKERS_AND_REPEAT_DRY_RUN");
    }

    /** 判断单表是否满足加入 actualDataNodes 的全部结构和号段门禁。 */
    private boolean isPublicationReadyTable(ShardingTablePreCreateTableResult tableResult) {
        return Set.of("CREATED", "SKIPPED").contains(tableResult.getStatus())
                && "MATCHED".equals(tableResult.getSchemaCheckStatus())
                && "MATCHED".equals(tableResult.getShardingTimeCheckStatus())
                && "MATCHED".equals(tableResult.getCharsetCheckStatus())
                && "MATCHED".equals(tableResult.getAutoIncrementCheckStatus());
    }

    /**
     * 复制只影响 checksum 的规则字段，生成候选版本；不复制运行模式或服务白名单。
     */
    private TransactionShardingProperties copyTransactionRule(String candidateVersion, Set<String> verifiedNodes) {
        TransactionShardingProperties candidate = new TransactionShardingProperties();
        candidate.setRuleVersion(candidateVersion);
        candidate.setDatabaseZoneId(transactionShardingProperties.getDatabaseZoneId());
        candidate.setShardingColumn(transactionShardingProperties.getShardingColumn());
        candidate.setPrimaryDataSource(transactionShardingProperties.getPrimaryDataSource());
        candidate.setReplicaDataSources(transactionShardingProperties.getReplicaDataSources());
        candidate.setPhysicalNodes(new ArrayList<>(verifiedNodes));
        candidate.setLogicTables(TransactionShardingProperties.defaultLogicTables());
        TransactionShardingProperties.QueryBudget budget = new TransactionShardingProperties.QueryBudget();
        budget.setSynchronousTimeoutMillis(
                transactionShardingProperties.getQueryBudget().getSynchronousTimeoutMillis());
        budget.setMaxResultRows(transactionShardingProperties.getQueryBudget().getMaxResultRows());
        budget.setMaxConcurrentExportsPerUser(
                transactionShardingProperties.getQueryBudget().getMaxConcurrentExportsPerUser());
        candidate.setQueryBudget(budget);
        return candidate;
    }

    /**
     * 将治理异常收敛为不含 SQL、连接信息和数据库地址的稳定摘要。
     */
    private String safeFailure(RuntimeException exception) {
        return "sharding governance operation failed: " + exception.getClass().getSimpleName();
    }

    private void collectSummary(ShardingTablePreCreateResult result, ShardingTablePreCreateTableResult tableResult) {
        switch (tableResult.getStatus()) {
            case "CREATED" -> result.getCreatedTables().add(tableResult.getPhysicalTable());
            case "SKIPPED", "DRY_RUN" -> result.getSkippedTables().add(tableResult.getPhysicalTable());
            case "MISMATCHED" -> result.getSchemaMismatchTables().add(tableResult.getPhysicalTable());
            case "FAILED" -> result.getFailedTables().add(tableResult.getPhysicalTable());
            default -> result.getWarnings().add("unknown sharding table status: " + tableResult.getStatus());
        }
    }

    private void savePhysicalTable(TransactionShardingGovernanceProperties.TableRule rule,
                                   ShardingQuarter quarter,
                                   ShardingTablePreCreateTableResult tableResult,
                                   boolean existedBefore,
                                   String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        SysShardingPhysicalTableDO record = findPhysicalTable(tableResult.getPhysicalTable());
        boolean insert = record == null;
        if (insert) {
            record = new SysShardingPhysicalTableDO();
            record.setCreateTime(now);
        }
        record.setLogicalTable(rule.getLogicalTable());
        record.setTemplateTable(tableResult.getTemplateTable());
        record.setPhysicalTable(tableResult.getPhysicalTable());
        record.setShardingColumn(rule.getShardingColumn());
        record.setStrategy(shardingProperties.getStrategy());
        record.setYear(quarter.year());
        record.setQuarter(quarter.quarter());
        record.setQuarterSuffix(quarter.suffix());
        record.setDataSource(rule.getActualDataSource());
        record.setTableStatus(toTableStatus(tableResult.getStatus(), existedBefore));
        record.setAutoCreated("CREATED".equals(tableResult.getStatus()) ? 1 : 0);
        record.setAutoIncrementStart(tableResult.getAutoIncrementStart());
        record.setAutoIncrementCurrent(tableResult.getAutoIncrementCurrent());
        record.setAutoIncrementMax(tableResult.getAutoIncrementMax());
        record.setSchemaCheckStatus(tableResult.getSchemaCheckStatus());
        record.setLastCheckTime(now);
        if ("CREATED".equals(tableResult.getStatus()) && !existedBefore) {
            record.setCreatedTime(now);
        }
        record.setErrorMessage(errorMessage);
        record.setUpdateTime(now);
        if (insert) {
            physicalTableMapper.insert(record);
        } else {
            physicalTableMapper.updateById(record);
        }
    }

    private SysShardingPhysicalTableDO findPhysicalTable(String physicalTable) {
        return physicalTableMapper.selectOne(new LambdaQueryWrapper<SysShardingPhysicalTableDO>()
                .eq(SysShardingPhysicalTableDO::getPhysicalTable, physicalTable)
                .last("LIMIT 1"));
    }

    private String toTableStatus(String status, boolean existedBefore) {
        return switch (status) {
            case "CREATED" -> "CREATED";
            case "SKIPPED", "MISMATCHED" -> "EXISTS";
            case "DRY_RUN" -> existedBefore ? "EXISTS" : "MISSING";
            case "FAILED" -> "FAILED";
            default -> "MISSING";
        };
    }

    private void saveCreateLog(String batchNo,
                               ShardingTablePreCreateRequest request,
                               JobExecuteContext context,
                               ShardingTablePreCreateResult result,
                               Instant start,
                               String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        SysShardingTableCreateLogDO log = new SysShardingTableCreateLogDO();
        log.setBatchNo(batchNo);
        log.setTriggerType(context == null || context.getTriggerType() == null ? "MANUAL" : context.getTriggerType().name());
        log.setDryRun(Boolean.TRUE.equals(request.getDryRun()) ? 1 : 0);
        log.setTargetQuarters(String.join(",", result.getTargetQuarters()));
        log.setPlannedCount(result.getTableResults().size());
        log.setCreatedCount(result.getCreatedTables().size());
        log.setSkippedCount(result.getSkippedTables().size());
        log.setFailedCount(result.getFailedTables().size());
        log.setSchemaMismatchCount(result.getSchemaMismatchTables().size());
        log.setRunStatus(runStatus(result, errorMessage));
        log.setResultSummary(JsonUtils.toJsonString(result));
        log.setErrorMessage(errorMessage);
        log.setStartTime(LocalDateTime.ofInstant(start, quarterResolver.zoneId(shardingProperties)));
        log.setEndTime(now);
        log.setDurationMs(Duration.between(start, Instant.now()).toMillis());
        log.setOperatorId(context == null ? null : context.getOperatorId());
        log.setOperatorName(context == null ? null : context.getOperatorName());
        log.setCreateTime(now);
        createLogMapper.insert(log);
    }

    private String runStatus(ShardingTablePreCreateResult result, String errorMessage) {
        if (StringUtils.hasText(errorMessage)) {
            return "FAILED";
        }
        if (!CollectionUtils.isEmpty(result.getFailedTables()) || !CollectionUtils.isEmpty(result.getSchemaMismatchTables())) {
            return "PARTIAL_FAILED";
        }
        return "SUCCESS";
    }
}
