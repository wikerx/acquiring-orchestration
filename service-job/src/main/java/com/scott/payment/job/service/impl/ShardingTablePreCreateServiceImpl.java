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

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateServiceImpl
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTablePreCreateServiceImpl 服务实现，用于执行领域规则、数据读写编排和业务异常转换，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class ShardingTablePreCreateServiceImpl implements ShardingTablePreCreateService {

    /**
     * sharding Properties 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentQuarterShardingProperties shardingProperties;
    /**
     * quarter Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingQuarterResolver quarterResolver;
    /**
     * table Name Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingPhysicalTableNameResolver tableNameResolver;
    /**
     * auto Increment Value Calculator 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingAutoIncrementValueCalculator autoIncrementValueCalculator;
    /**
     * schema Inspector 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingTableSchemaInspector schemaInspector;
    /**
     * ddl Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingTableDdlService ddlService;
    /**
     * physical Table Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysShardingPhysicalTableMapper physicalTableMapper;
    /**
     * create Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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

    /**
     * 执行 target Quarters 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
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

/**
 * 执行 process Rule 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
 * @param targetQuarters target Quarters 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
 * @param result result 输入值，含义由调用方法名称和所属业务对象限定
 */
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

/**
 * 执行 process Table 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
 * @param quarter quarter 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
 * @return 方法签名声明的返回值，具体结构由返回类型定义
 */
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

    /**
     * 执行 collect Summary 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param result result 输入值，含义由调用方法名称和所属业务对象限定
     * @param tableResult table Result 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void collectSummary(ShardingTablePreCreateResult result, ShardingTablePreCreateTableResult tableResult) {
        switch (tableResult.getStatus()) {
            case "CREATED" -> result.getCreatedTables().add(tableResult.getPhysicalTable());
            case "SKIPPED", "DRY_RUN" -> result.getSkippedTables().add(tableResult.getPhysicalTable());
            case "MISMATCHED" -> result.getSchemaMismatchTables().add(tableResult.getPhysicalTable());
            case "FAILED" -> result.getFailedTables().add(tableResult.getPhysicalTable());
            default -> result.getWarnings().add("unknown sharding table status: " + tableResult.getStatus());
        }
    }

/**
 * 执行 save Physical Table 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
 * @param quarter quarter 输入值，含义由调用方法名称和所属业务对象限定
 * @param tableResult table Result 输入值，含义由调用方法名称和所属业务对象限定
 * @param existedBefore existed Before 输入值，含义由调用方法名称和所属业务对象限定
 * @param errorMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 */
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

    /**
     * 执行 find Physical Table 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param physicalTable physical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private SysShardingPhysicalTableDO findPhysicalTable(String physicalTable) {
        return physicalTableMapper.selectOne(new LambdaQueryWrapper<SysShardingPhysicalTableDO>()
                .eq(SysShardingPhysicalTableDO::getPhysicalTable, physicalTable)
                .last("LIMIT 1"));
    }

    /**
     * 执行 to Table Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param existedBefore existed Before 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private String toTableStatus(String status, boolean existedBefore) {
        return switch (status) {
            case "CREATED" -> "CREATED";
            case "SKIPPED", "MISMATCHED" -> "EXISTS";
            case "DRY_RUN" -> existedBefore ? "EXISTS" : "MISSING";
            case "FAILED" -> "FAILED";
            default -> "MISSING";
        };
    }

/**
 * 执行 save Create Log 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param batchNo batch No 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
 * @param context context 输入值，含义由调用方法名称和所属业务对象限定
 * @param result result 输入值，含义由调用方法名称和所属业务对象限定
 * @param start start 输入值，含义由调用方法名称和所属业务对象限定
 * @param errorMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 */
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

    /**
     * 执行 run Status 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：调度任务服务层；输入来源、输出结构和异常语义由 ShardingTablePreCreateServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param result result 输入值，含义由调用方法名称和所属业务对象限定
     * @param errorMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
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
