package com.scott.payment.admin.application.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.client.job.JobSchedulerInternalClient;
import com.scott.payment.admin.client.job.dto.ShardingTablePreCreateRemoteRequest;
import com.scott.payment.admin.converter.ShardingGovernanceConverter;
import com.scott.payment.admin.dto.monitor.ShardingIdRuleResponse;
import com.scott.payment.admin.dto.monitor.ShardingPhysicalTableQueryRequest;
import com.scott.payment.admin.dto.monitor.ShardingPhysicalTableResponse;
import com.scott.payment.admin.dto.monitor.ShardingRuleResponse;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateLogQueryRequest;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateLogResponse;
import com.scott.payment.admin.dto.monitor.ShardingTableCreateRequest;
import com.scott.payment.admin.dto.monitor.ShardingTablePreCreateResultResponse;
import com.scott.payment.admin.entity.SysShardingPhysicalTableDO;
import com.scott.payment.admin.entity.SysShardingTableCreateLogDO;
import com.scott.payment.admin.mapper.SysShardingPhysicalTableMapper;
import com.scott.payment.admin.mapper.SysShardingTableCreateLogMapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementRange;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementValueCalculator;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminShardingGovernanceApplicationService
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : AdminShardingGovernanceApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminShardingGovernanceApplicationService {

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
     * job Scheduler Internal Client 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobSchedulerInternalClient jobSchedulerInternalClient;
    /**
     * 分表治理对象转换器。
     */
    private final ShardingGovernanceConverter shardingGovernanceConverter;

    /**
     * 创建分表治理应用服务。
     *
     * @param shardingProperties            分表配置
     * @param quarterResolver               季度解析器
     * @param tableNameResolver             物理表名解析器
     * @param autoIncrementValueCalculator  自增区间计算器
     * @param physicalTableMapper           物理表登记 Mapper
     * @param createLogMapper               建表日志 Mapper
     * @param jobSchedulerInternalClient   调度中心内部客户端
     * @param shardingGovernanceConverter  分表治理对象转换器
     */
    public AdminShardingGovernanceApplicationService(PaymentQuarterShardingProperties shardingProperties,
                                                     ShardingQuarterResolver quarterResolver,
                                                     ShardingPhysicalTableNameResolver tableNameResolver,
                                                     ShardingAutoIncrementValueCalculator autoIncrementValueCalculator,
                                                     SysShardingPhysicalTableMapper physicalTableMapper,
                                                     SysShardingTableCreateLogMapper createLogMapper,
                                                     JobSchedulerInternalClient jobSchedulerInternalClient,
                                                     ShardingGovernanceConverter shardingGovernanceConverter) {
        this.shardingProperties = shardingProperties;
        this.quarterResolver = quarterResolver;
        this.tableNameResolver = tableNameResolver;
        this.autoIncrementValueCalculator = autoIncrementValueCalculator;
        this.physicalTableMapper = physicalTableMapper;
        this.createLogMapper = createLogMapper;
        this.jobSchedulerInternalClient = jobSchedulerInternalClient;
        this.shardingGovernanceConverter = shardingGovernanceConverter;
    }

    /**
     * 查询所有分表规则。
     *
     * @return 分表规则列表
     */
    public List<ShardingRuleResponse> listRules() {
        return shardingProperties.getTables().entrySet().stream()
                .map(this::toRuleResponse)
                .toList();
    }

    /**
     * 查询单个分表规则。
     *
     * @param logicalTable 逻辑表或规则 key
     * @return 分表规则
     */
    public ShardingRuleResponse getRule(String logicalTable) {
        Map.Entry<String, PaymentQuarterShardingProperties.TableRule> entry = findRule(logicalTable);
        return toRuleResponse(entry);
    }

    /**
     * 分页查询分表物理表登记。
     *
     * @param request 查询条件
     * @return 物理表分页结果
     */
    public PageResult<ShardingPhysicalTableResponse> pagePhysicalTables(ShardingPhysicalTableQueryRequest request) {
        ShardingPhysicalTableQueryRequest query = request == null ? new ShardingPhysicalTableQueryRequest() : request;
        Page<SysShardingPhysicalTableDO> page = physicalTableMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildPhysicalTableWrapper(query)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream()
                        .map(shardingGovernanceConverter::toPhysicalTableResponse)
                        .toList()
        );
    }

    /**
     * 查询分表物理表详情。
     *
     * @param id 物理表登记主键
     * @return 物理表详情
     */
    public ShardingPhysicalTableResponse getPhysicalTable(Long id) {
        SysShardingPhysicalTableDO entity = physicalTableMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        return shardingGovernanceConverter.toPhysicalTableResponse(entity);
    }

    /**
     * 刷新物理表登记状态。
     *
     * <p>刷新只探测表是否存在并更新治理表登记，不做结构比对，也不会执行 DDL。</p>
     *
     * @param request      刷新范围
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @return 刷新结果
     */
    public ShardingTablePreCreateResultResponse refreshPhysicalTables(ShardingTableCreateRequest request,
                                                                      String operatorId,
                                                                      String operatorName) {
        ShardingTableCreateRequest refreshRequest = copyRequest(request);
        refreshRequest.setCompareSchemaIfExists(Boolean.FALSE);
        return jobSchedulerInternalClient.dryRunShardingTableCreate(toRemoteRequest(refreshRequest, true, operatorId, operatorName));
    }

    /**
     * 检查物理表结构。
     *
     * <p>结构检查复用 service-job dry-run 链路，只对模板表和已存在物理表做比对，不执行 DDL。</p>
     *
     * @param request      检查范围
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @return 检查结果
     */
    public ShardingTablePreCreateResultResponse checkPhysicalTableSchema(ShardingTableCreateRequest request,
                                                                         String operatorId,
                                                                         String operatorName) {
        ShardingTableCreateRequest checkRequest = copyRequest(request);
        checkRequest.setCompareSchemaIfExists(Boolean.TRUE);
        return jobSchedulerInternalClient.dryRunShardingTableCreate(toRemoteRequest(checkRequest, true, operatorId, operatorName));
    }

    /**
     * 分页查询分表建表日志。
     *
     * @param request 查询条件
     * @return 建表日志分页结果
     */
    public PageResult<ShardingTableCreateLogResponse> pageCreateLogs(ShardingTableCreateLogQueryRequest request) {
        ShardingTableCreateLogQueryRequest query = request == null ? new ShardingTableCreateLogQueryRequest() : request;
        Page<SysShardingTableCreateLogDO> page = createLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildCreateLogWrapper(query)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream()
                        .map(shardingGovernanceConverter::toCreateLogResponse)
                        .toList()
        );
    }

    /**
     * 查询分表建表日志详情。
     *
     * @param id 建表日志主键
     * @return 建表日志详情
     */
    public ShardingTableCreateLogResponse getCreateLog(Long id) {
        SysShardingTableCreateLogDO entity = createLogMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        return shardingGovernanceConverter.toCreateLogResponse(entity);
    }

    /**
     * 预演分表物理表预创建。
     *
     * @param request      建表请求
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @return 预演结果
     */
    public ShardingTablePreCreateResultResponse dryRun(ShardingTableCreateRequest request,
                                                       String operatorId,
                                                       String operatorName) {
        return jobSchedulerInternalClient.dryRunShardingTableCreate(toRemoteRequest(request, true, operatorId, operatorName));
    }

    /**
     * 立即创建缺失的分表物理表。
     *
     * @param request      建表请求
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @return 建表结果
     */
    public ShardingTablePreCreateResultResponse execute(ShardingTableCreateRequest request,
                                                        String operatorId,
                                                        String operatorName) {
        return jobSchedulerInternalClient.executeShardingTableCreate(toRemoteRequest(request, false, operatorId, operatorName));
    }

    /**
     * 查询分表 ID 规则。
     *
     * @return ID 规则说明
     */
    public ShardingIdRuleResponse idRule() {
        ShardingQuarter currentQuarter = quarterResolver.currentQuarter(shardingProperties);
        ShardingAutoIncrementRange range = autoIncrementValueCalculator.calculate(shardingProperties, currentQuarter);
        PaymentQuarterShardingProperties.IdGenerator idGenerator = shardingProperties.getIdGenerator();
        ShardingIdRuleResponse response = new ShardingIdRuleResponse();
        response.setMode(idGenerator.getMode());
        response.setPrefixFormat(idGenerator.getPrefixFormat());
        response.setSequenceWidth(idGenerator.getSequenceWidth());
        response.setStartSequence(idGenerator.getStartSequence());
        response.setMaxSequence(idGenerator.getMaxSequence());
        response.setCurrentQuarter(currentQuarter.displayName());
        response.setCurrentQuarterStartValue(range.startValue());
        response.setCurrentQuarterMaxValue(range.maxValue());
        return response;
    }

    /**
     * 编排 to Rule Response 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param entry entry 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private ShardingRuleResponse toRuleResponse(Map.Entry<String, PaymentQuarterShardingProperties.TableRule> entry) {
        PaymentQuarterShardingProperties.TableRule rule = entry.getValue();
        ShardingQuarter currentQuarter = quarterResolver.currentQuarter(shardingProperties);
        ShardingRuleResponse response = new ShardingRuleResponse();
        response.setRuleKey(entry.getKey());
        response.setLogicalTable(rule.getLogicalTable());
        response.setTemplateTable(rule.getTemplateTable());
        response.setEnabled(Boolean.TRUE.equals(rule.getEnabled()));
        response.setIdColumn(rule.getIdColumn());
        response.setShardingColumn(rule.getShardingColumn());
        response.setActualDataSource(rule.getActualDataSource());
        response.setDescription(rule.getDescription());
        response.setStartYear(rule.getStartYear());
        response.setStartQuarter(rule.getStartQuarter());
        response.setEndYear(rule.getEndYear());
        response.setEndQuarter(rule.getEndQuarter());
        response.setTableNameFormat(rule.getTableNameFormat());
        response.setCurrentPhysicalTable(resolvePhysicalTableName(rule, currentQuarter));
        response.setNextPhysicalTable(resolvePhysicalTableName(rule, currentQuarter.next()));
        List<String> physicalTables = resolvePhysicalTables(rule);
        response.setPhysicalTables(physicalTables);
        response.setPhysicalTableCount(physicalTables.size());
        return response;
    }

    /**
     * 编排 resolve Physical Tables 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<String> resolvePhysicalTables(PaymentQuarterShardingProperties.TableRule rule) {
        return quarterResolver.quartersInRange(rule).stream()
                .map(quarter -> tableNameResolver.physicalTableName(rule, quarter))
                .toList();
    }

    /**
     * 编排 resolve Physical Table Name 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param rule rule 输入值，含义由调用方法名称和所属业务对象限定
     * @param quarter quarter 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolvePhysicalTableName(PaymentQuarterShardingProperties.TableRule rule, ShardingQuarter quarter) {
        if (!quarterResolver.inRange(rule, quarter)) {
            return null;
        }
        return tableNameResolver.physicalTableName(rule, quarter);
    }

    /**
     * 编排 find Rule 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private Map.Entry<String, PaymentQuarterShardingProperties.TableRule> findRule(String logicalTable) {
        if (!StringUtils.hasText(logicalTable)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "logicalTable is required");
        }
        return shardingProperties.getTables().entrySet().stream()
                .filter(entry -> logicalTable.equals(entry.getKey())
                        || logicalTable.equals(entry.getValue().getLogicalTable()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ApiResultEnum.NOT_FOUND));
    }

    /**
     * 编排 build Physical Table Wrapper 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private LambdaQueryWrapper<SysShardingPhysicalTableDO> buildPhysicalTableWrapper(ShardingPhysicalTableQueryRequest query) {
        return new LambdaQueryWrapper<SysShardingPhysicalTableDO>()
                .eq(StringUtils.hasText(query.getLogicalTable()), SysShardingPhysicalTableDO::getLogicalTable, trim(query.getLogicalTable()))
                .like(StringUtils.hasText(query.getPhysicalTable()), SysShardingPhysicalTableDO::getPhysicalTable, trim(query.getPhysicalTable()))
                .eq(query.getYear() != null, SysShardingPhysicalTableDO::getYear, query.getYear())
                .eq(query.getQuarter() != null, SysShardingPhysicalTableDO::getQuarter, query.getQuarter())
                .eq(StringUtils.hasText(query.getTableStatus()), SysShardingPhysicalTableDO::getTableStatus, trim(query.getTableStatus()))
                .eq(StringUtils.hasText(query.getSchemaCheckStatus()), SysShardingPhysicalTableDO::getSchemaCheckStatus, trim(query.getSchemaCheckStatus()))
                .orderByDesc(SysShardingPhysicalTableDO::getYear)
                .orderByDesc(SysShardingPhysicalTableDO::getQuarter)
                .orderByAsc(SysShardingPhysicalTableDO::getLogicalTable)
                .orderByAsc(SysShardingPhysicalTableDO::getPhysicalTable);
    }

    /**
     * 编排 build Create Log Wrapper 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private LambdaQueryWrapper<SysShardingTableCreateLogDO> buildCreateLogWrapper(ShardingTableCreateLogQueryRequest query) {
        return new LambdaQueryWrapper<SysShardingTableCreateLogDO>()
                .like(StringUtils.hasText(query.getBatchNo()), SysShardingTableCreateLogDO::getBatchNo, trim(query.getBatchNo()))
                .eq(StringUtils.hasText(query.getTriggerType()), SysShardingTableCreateLogDO::getTriggerType, trim(query.getTriggerType()))
                .eq(query.getDryRun() != null, SysShardingTableCreateLogDO::getDryRun, query.getDryRun())
                .eq(StringUtils.hasText(query.getRunStatus()), SysShardingTableCreateLogDO::getRunStatus, trim(query.getRunStatus()))
                .orderByDesc(SysShardingTableCreateLogDO::getCreateTime)
                .orderByDesc(SysShardingTableCreateLogDO::getId);
    }

/**
 * 编排 to Remote Request 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
 * <p>
 * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
 * @param dryRun dry Run 输入值，含义由调用方法名称和所属业务对象限定
 * @param operatorId operator Id 输入值，含义由调用方法名称和所属业务对象限定
 * @param operatorName operator Name 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
 */
    private ShardingTablePreCreateRemoteRequest toRemoteRequest(ShardingTableCreateRequest request,
                                                                boolean dryRun,
                                                                String operatorId,
                                                                String operatorName) {
        ShardingTableCreateRequest safeRequest = request == null ? new ShardingTableCreateRequest() : request;
        ShardingTablePreCreateRemoteRequest remoteRequest = shardingGovernanceConverter.toRemoteRequest(
                safeRequest,
                dryRun,
                operatorId,
                operatorName
        );
        remoteRequest.setLogicalTables(safeRequest.getLogicalTables() == null ? List.of() : safeRequest.getLogicalTables());
        return remoteRequest;
    }

    /**
     * 编排 copy Request 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ShardingTableCreateRequest copyRequest(ShardingTableCreateRequest request) {
        ShardingTableCreateRequest source = request == null ? new ShardingTableCreateRequest() : request;
        ShardingTableCreateRequest target = new ShardingTableCreateRequest();
        target.setIncludeCurrentQuarter(source.getIncludeCurrentQuarter());
        target.setIncludeNextQuarter(source.getIncludeNextQuarter());
        target.setLogicalTables(source.getLogicalTables() == null ? List.of() : source.getLogicalTables());
        target.setCompareSchemaIfExists(source.getCompareSchemaIfExists());
        return target;
    }

    /**
     * 编排 trim 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminShardingGovernanceApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
