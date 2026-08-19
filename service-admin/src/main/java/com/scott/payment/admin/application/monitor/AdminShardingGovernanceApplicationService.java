package com.scott.payment.admin.application.monitor;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
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
import com.scott.payment.component.db.sharding.ShardingAutoIncrementRange;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementValueCalculator;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.TransactionShardingGovernanceProperties;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminShardingGovernanceApplicationService
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Admin Sharding Governance Application Service 应用服务，位于 运营后台服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
public class AdminShardingGovernanceApplicationService {

    /**
     * sharding Properties，用于保存 Admin Sharding Governance Application Service 中与 shardingproperties 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionShardingGovernanceProperties shardingProperties;

    /** 当前实例加载的 ShardingSphere 交易规则元数据。 */
    private final TransactionShardingProperties transactionShardingProperties;

    /**
     * quarter Resolver，用于保存 Admin Sharding Governance Application Service 中与 quarterresolver 相关的业务属性。
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
     * auto Increment Value Calculator，用于保存 Admin Sharding Governance Application Service 中与 autoincrementvaluecalculator 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ShardingAutoIncrementValueCalculator autoIncrementValueCalculator;

    /**
     * physical Table Mapper 依赖，用于 Admin Sharding Governance Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysShardingPhysicalTableMapper physicalTableMapper;

    /**
     * create Log Mapper 依赖，用于 Admin Sharding Governance Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysShardingTableCreateLogMapper createLogMapper;

    /**
     * job Scheduler Internal Client 依赖，用于 Admin Sharding Governance Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * @param shardingProperties            物理表治理配置
     * @param transactionShardingProperties ShardingSphere 交易规则元数据
     * @param quarterResolver               季度解析器
     * @param tableNameResolver             物理表名解析器
     * @param autoIncrementValueCalculator  自增区间计算器
     * @param physicalTableMapper           物理表登记 Mapper
     * @param createLogMapper               建表日志 Mapper
     * @param jobSchedulerInternalClient   调度中心内部客户端
     * @param shardingGovernanceConverter  分表治理对象转换器
     */
    public AdminShardingGovernanceApplicationService(
                                                     TransactionShardingGovernanceProperties shardingProperties,
                                                     TransactionShardingProperties transactionShardingProperties,
                                                     ShardingQuarterResolver quarterResolver,
                                                     ShardingPhysicalTableNameResolver tableNameResolver,
                                                     ShardingAutoIncrementValueCalculator autoIncrementValueCalculator,
                                                     SysShardingPhysicalTableMapper physicalTableMapper,
                                                     SysShardingTableCreateLogMapper createLogMapper,
                                                     JobSchedulerInternalClient jobSchedulerInternalClient,
                                                     ShardingGovernanceConverter shardingGovernanceConverter) {
        this.shardingProperties = shardingProperties;
        this.transactionShardingProperties = transactionShardingProperties;
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
    @DS(DataSourceName.SLAVE)
    public List<ShardingRuleResponse> listRules() {
        return configuredRules().entrySet().stream()
                .map(this::toRuleResponse)
                .toList();
    }

    /**
     * 查询单个分表规则。
     *
     * @param logicalTable 逻辑表或规则 key
     * @return 分表规则
     */
    @DS(DataSourceName.SLAVE)
    public ShardingRuleResponse getRule(String logicalTable) {
        Map.Entry<String, TransactionShardingGovernanceProperties.TableRule> entry = findRule(logicalTable);
        return toRuleResponse(entry);
    }

    /**
     * 分页查询分表物理表登记。
     *
     * @param request 查询条件
     * @return 物理表分页结果
     */
    @DS(DataSourceName.SLAVE)
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
                        .map(this::enrichPhysicalTableResponse)
                        .toList()
        );
    }

    /**
     * 查询分表物理表详情。
     *
     * @param id 物理表登记主键
     * @return 物理表详情
     */
    @DS(DataSourceName.SLAVE)
    public ShardingPhysicalTableResponse getPhysicalTable(Long id) {
        SysShardingPhysicalTableDO entity = physicalTableMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        return enrichPhysicalTableResponse(shardingGovernanceConverter.toPhysicalTableResponse(entity));
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
    @DS(DataSourceName.SLAVE)
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
    @DS(DataSourceName.SLAVE)
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
        TransactionShardingGovernanceProperties.IdGenerator idGenerator = shardingProperties.getIdGenerator();
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
     * 构造规则响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param entry entry 输入值，参与 entry 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ShardingRuleResponse toRuleResponse(Map.Entry<String, TransactionShardingGovernanceProperties.TableRule> entry) {
        TransactionShardingGovernanceProperties.TableRule rule = entry.getValue();
        ShardingQuarter currentQuarter = quarterResolver.currentQuarter(shardingProperties);
        ShardingRuleResponse response = new ShardingRuleResponse();
        response.setRuleKey(entry.getKey());
        response.setRuleVersion(transactionShardingProperties.getRuleVersion());
        response.setRuleChecksumPrefix(checksumPrefix());
        response.setVerifiedPhysicalNodes(transactionShardingProperties.getPhysicalNodes());
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
        ShardingQuarter nextQuarter = currentQuarter.next();
        response.setCurrentPhysicalTable(resolvePhysicalTableName(rule, currentQuarter));
        response.setNextPhysicalTable(resolvePhysicalTableName(rule, nextQuarter));
        response.setCurrentNodeRegistered(transactionShardingProperties.getPhysicalNodes().contains(currentQuarter.suffix()));
        response.setNextNodeRegistered(transactionShardingProperties.getPhysicalNodes().contains(nextQuarter.suffix()));
        List<String> physicalTables = resolvePhysicalTables(rule);
        response.setPhysicalTables(physicalTables);
        response.setPhysicalTableCount(physicalTables.size());
        return response;
    }

    /**
     * 解析resolve物理表，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private List<String> resolvePhysicalTables(TransactionShardingGovernanceProperties.TableRule rule) {
        ShardingQuarter cursor = quarterResolver.currentQuarter(shardingProperties);
        int horizon = Math.max(shardingProperties.getPlanningHorizonQuarters(), 1);
        List<String> physicalTables = new ArrayList<>(horizon);
        for (int index = 0; index < horizon; index++) {
            if (quarterResolver.inRange(rule, cursor)) {
                physicalTables.add(tableNameResolver.physicalTableName(rule, cursor));
            }
            cursor = cursor.next();
        }
        return physicalTables;
    }

    /**
     * 解析resolve物理表name，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param rule rule 输入值，参与 规则 的查询、校验、转换、写入或日志摘要
     * @param quarter quarter 输入值，参与 quarter 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolvePhysicalTableName(TransactionShardingGovernanceProperties.TableRule rule, ShardingQuarter quarter) {
        if (!quarterResolver.inRange(rule, quarter)) {
            return null;
        }
        return tableNameResolver.physicalTableName(rule, quarter);
    }

    /**
     * 查询规则，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param logicalTable 逻辑表名，用于按交易时间解析真实物理分表
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Map.Entry<String, TransactionShardingGovernanceProperties.TableRule> findRule(String logicalTable) {
        if (!StringUtils.hasText(logicalTable)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "logicalTable is required");
        }
        return configuredRules().entrySet().stream()
                .filter(entry -> logicalTable.equals(entry.getKey())
                        || logicalTable.equals(entry.getValue().getLogicalTable()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ApiResultEnum.NOT_FOUND));
    }

    /**
     * 只返回固定的正式交易表，拒绝任何 test_* 或未知治理规则进入页面和预建入口。
     */
    private Map<String, TransactionShardingGovernanceProperties.TableRule> configuredRules() {
        Set<String> formalTables = Set.copyOf(TransactionShardingProperties.defaultLogicTables());
        Map<String, TransactionShardingGovernanceProperties.TableRule> result = new LinkedHashMap<>();
        shardingProperties.getTables().forEach((key, rule) -> {
            if (rule != null && formalTables.contains(rule.getLogicalTable())) {
                result.put(key, rule);
            }
        });
        return result;
    }

    /** 为物理表登记响应补充当前规则版本和 actualDataNodes 登记状态。 */
    private ShardingPhysicalTableResponse enrichPhysicalTableResponse(ShardingPhysicalTableResponse response) {
        response.setRuleVersion(transactionShardingProperties.getRuleVersion());
        response.setRuleChecksumPrefix(checksumPrefix());
        response.setNodeRegistered(TransactionShardingProperties.defaultLogicTables().contains(response.getLogicalTable())
                && response.getQuarterSuffix() != null
                && transactionShardingProperties.getPhysicalNodes().contains(response.getQuarterSuffix()));
        return response;
    }

    /**
     * 截取可公开展示的 checksum 前缀，避免管理接口输出完整治理标识。
     *
     * @return sha256 前缀和前 12 位摘要；规则未发布时返回 null
     */
    private String checksumPrefix() {
        String checksum = transactionShardingProperties.getRuleChecksum();
        if (!StringUtils.hasText(checksum)) {
            return null;
        }
        return checksum.substring(0, Math.min(checksum.length(), 19));
    }

    /**
     * 构造物理tablewrapper对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
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
     * 构造create日志wrapper对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 构造、转换或解析后的业务值
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
 * 构造remote请求对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param dryRun dry Run 输入值，参与 dryrun 的查询、校验、转换、写入或日志摘要
 * @param operatorId operator ID 输入值，参与 operatorID 的查询、校验、转换、写入或日志摘要
 * @param operatorName operator Name 输入值，参与 operatorname 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
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
     * 构造请求对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化trim，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
