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
 * @description : Sharding Table Pre Create Service Impl 服务实现，位于 调度任务服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class ShardingTablePreCreateServiceImpl implements ShardingTablePreCreateService {

    /**
     * sharding Properties，用于保存 Sharding Table Pre Create Service Impl 中与 shardingproperties 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentQuarterShardingProperties shardingProperties;
    /**
     * quarter Resolver，用于保存 Sharding Table Pre Create Service Impl 中与 quarterresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ShardingQuarterResolver quarterResolver;
    /**
     * table Name Resolver，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ShardingPhysicalTableNameResolver tableNameResolver;
    /**
     * auto Increment Value Calculator，用于保存 Sharding Table Pre Create Service Impl 中与 autoincrementvaluecalculator 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ShardingAutoIncrementValueCalculator autoIncrementValueCalculator;
    /**
     * schema Inspector，用于保存 Sharding Table Pre Create Service Impl 中与 schemainspector 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ShardingTableSchemaInspector schemaInspector;
    /**
     * ddl Service 依赖，用于 Sharding Table Pre Create Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ShardingTableDdlService ddlService;
    /**
     * physical Table Mapper 依赖，用于 Sharding Table Pre Create Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysShardingPhysicalTableMapper physicalTableMapper;
    /**
     * create Log Mapper 依赖，用于 Sharding Table Pre Create Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 整理目标分表季度，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
 * 处理规则流程，串联校验、状态判断和后续业务动作。
 * <p>
 * 前置条件：调用方已把 调度任务服务 的请求、消息或任务参数解析为当前方法可识别的模型。
 * 该方法按业务分支串联校验、状态判断、数据读写、远程调用或消息投递，关键阶段应保留 traceId 日志。
 * 异常边界：幂等冲突、状态不允许、外部系统失败或持久化失败按当前流程返回明确结果。
 * </p>
 * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
 * @param targetQuarters target Quarters 输入值，参与 targetquarters 的查询、校验、转换、写入或日志摘要
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
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
 * 处理table流程，串联校验、状态判断和后续业务动作。
 * <p>
 * 前置条件：调用方已把 调度任务服务 的请求、消息或任务参数解析为当前方法可识别的模型。
 * 该方法按业务分支串联校验、状态判断、数据读写、远程调用或消息投递，关键阶段应保留 traceId 日志。
 * 异常边界：幂等冲突、状态不允许、外部系统失败或持久化失败按当前流程返回明确结果。
 * </p>
 * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
 * @param quarter quarter 输入值，参与 quarter 的查询、校验、转换、写入或日志摘要
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理扫描汇总结果，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param tableResult table Result 输入值，参与 表结果 的查询、校验、转换、写入或日志摘要
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
 * 创建物理table，完成必要校验后写入或委托下游服务处理。
 * <p>
 * 前置条件：调用方已完成 调度任务服务 的身份、权限、必填字段和业务唯一性准备。
 * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
 * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
 * </p>
 * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
 * @param quarter quarter 输入值，参与 quarter 的查询、校验、转换、写入或日志摘要
 * @param tableResult table Result 输入值，参与 table结果 的查询、校验、转换、写入或日志摘要
 * @param existedBefore existed Before 输入值，参与 existedbefore 的查询、校验、转换、写入或日志摘要
 * @param errorMessage error Message 输入值，参与 错误说明 的查询、校验、转换、写入或日志摘要
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
     * 查询物理table，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 调度任务服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param physicalTable 经分表规则解析后的物理表名，只允许来自受控分表解析器
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private SysShardingPhysicalTableDO findPhysicalTable(String physicalTable) {
        return physicalTableMapper.selectOne(new LambdaQueryWrapper<SysShardingPhysicalTableDO>()
                .eq(SysShardingPhysicalTableDO::getPhysicalTable, physicalTable)
                .last("LIMIT 1"));
    }

    /**
     * 构造table状态对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param existedBefore existed Before 输入值，参与 existedbefore 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
 * 创建create日志，完成必要校验后写入或委托下游服务处理。
 * <p>
 * 前置条件：调用方已完成 调度任务服务 的身份、权限、必填字段和业务唯一性准备。
 * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
 * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
 * </p>
 * @param batchNo batch No 输入值，参与 batchno 的查询、校验、转换、写入或日志摘要
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param context context 输入值，参与 context 的查询、校验、转换、写入或日志摘要
 * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
 * @param start start 输入值，参与 start 的查询、校验、转换、写入或日志摘要
 * @param errorMessage error Message 输入值，参与 错误说明 的查询、校验、转换、写入或日志摘要
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
     * 整理任务运行状态，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param errorMessage error Message 输入值，参与 错误说明 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
