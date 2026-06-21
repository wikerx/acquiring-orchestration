package com.scott.payment.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementRange;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementValueCalculator;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.ShardingTableDdlService;
import com.scott.payment.component.db.sharding.ShardingTableInspectionResult;
import com.scott.payment.component.db.sharding.ShardingTableSchemaInspector;
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
 * 分表物理表预创建服务实现。
 *
 * <p>该服务只处理分表治理测试表的物理表创建和登记，不接入真实支付交易写入链路。</p>
 */
@Service
public class ShardingTablePreCreateServiceImpl implements ShardingTablePreCreateService {

    private final PaymentQuarterShardingProperties shardingProperties;
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
    public ShardingTablePreCreateServiceImpl(PaymentQuarterShardingProperties shardingProperties,
                                             ShardingQuarterResolver quarterResolver,
                                             ShardingPhysicalTableNameResolver tableNameResolver,
                                             ShardingAutoIncrementValueCalculator autoIncrementValueCalculator,
                                             ShardingTableSchemaInspector schemaInspector,
                                             ShardingTableDdlService ddlService,
                                             SysShardingPhysicalTableMapper physicalTableMapper,
                                             SysShardingTableCreateLogMapper createLogMapper) {
        this.shardingProperties = shardingProperties;
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
            List<Map.Entry<String, PaymentQuarterShardingProperties.TableRule>> rules = enabledRules(safeRequest);
            List<ShardingQuarter> targetQuarters = targetQuarters(safeRequest);
            result.setTargetQuarters(targetQuarters.stream().map(ShardingQuarter::displayName).toList());
            for (Map.Entry<String, PaymentQuarterShardingProperties.TableRule> entry : rules) {
                processRule(entry.getValue(), targetQuarters, safeRequest, result);
            }
            saveCreateLog(batchNo, safeRequest, context, result, start, null);
            return result;
        } catch (RuntimeException exception) {
            result.getWarnings().add(exception.getMessage());
            saveCreateLog(batchNo, safeRequest, context, result, start, exception.getMessage());
            throw exception;
        }
    }

    private ShardingTablePreCreateResult buildBaseResult(ShardingTablePreCreateRequest request) {
        ShardingTablePreCreateResult result = new ShardingTablePreCreateResult();
        ShardingQuarter currentQuarter = quarterResolver.currentQuarter(shardingProperties);
        result.setDryRun(Boolean.TRUE.equals(request.getDryRun()));
        result.setTimezone(shardingProperties.getDatabaseTimezone());
        result.setStrategy(shardingProperties.getStrategy());
        result.setCurrentQuarter(currentQuarter.displayName());
        return result;
    }

    private List<Map.Entry<String, PaymentQuarterShardingProperties.TableRule>> enabledRules(ShardingTablePreCreateRequest request) {
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

    private void processRule(PaymentQuarterShardingProperties.TableRule rule,
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

    private ShardingTablePreCreateTableResult processTable(PaymentQuarterShardingProperties.TableRule rule,
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
                inspection = exists && Boolean.TRUE.equals(request.getCompareSchemaIfExists())
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
            tableResult.setAutoIncrementCurrent(inspection.getAutoIncrementCurrent());
            if (Objects.equals("MISMATCHED", inspection.getSchemaCheckStatus())) {
                tableResult.setStatus("MISMATCHED");
                tableResult.setMessage(inspection.getMessage());
            }
            savePhysicalTable(rule, quarter, tableResult, exists, null);
            return tableResult;
        } catch (RuntimeException exception) {
            tableResult.setStatus("FAILED");
            tableResult.setSchemaCheckStatus("FAILED");
            tableResult.setMessage(exception.getMessage());
            savePhysicalTable(rule, quarter, tableResult, false, exception.getMessage());
            return tableResult;
        }
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

    private void savePhysicalTable(PaymentQuarterShardingProperties.TableRule rule,
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
