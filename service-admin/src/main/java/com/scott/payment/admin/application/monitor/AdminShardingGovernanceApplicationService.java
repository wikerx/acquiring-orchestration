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

/**
 * 管理后台分表治理应用服务。
 *
 * <p>负责聚合 Nacos 分表规则、治理表登记状态和 service-job 预建表能力，
 * 供系统监控下的分表治理菜单查询和触发。</p>
 */
@Service
public class AdminShardingGovernanceApplicationService {

    private final PaymentQuarterShardingProperties shardingProperties;
    private final ShardingQuarterResolver quarterResolver;
    private final ShardingPhysicalTableNameResolver tableNameResolver;
    private final ShardingAutoIncrementValueCalculator autoIncrementValueCalculator;
    private final SysShardingPhysicalTableMapper physicalTableMapper;
    private final SysShardingTableCreateLogMapper createLogMapper;
    private final JobSchedulerInternalClient jobSchedulerInternalClient;

    /**
     * 创建分表治理应用服务。
     *
     * @param shardingProperties            分表配置
     * @param quarterResolver               季度解析器
     * @param tableNameResolver             物理表名解析器
     * @param autoIncrementValueCalculator  自增区间计算器
     * @param physicalTableMapper           物理表登记 Mapper
     * @param createLogMapper               建表日志 Mapper
     * @param jobSchedulerInternalClient    调度中心内部客户端
     */
    public AdminShardingGovernanceApplicationService(PaymentQuarterShardingProperties shardingProperties,
                                                     ShardingQuarterResolver quarterResolver,
                                                     ShardingPhysicalTableNameResolver tableNameResolver,
                                                     ShardingAutoIncrementValueCalculator autoIncrementValueCalculator,
                                                     SysShardingPhysicalTableMapper physicalTableMapper,
                                                     SysShardingTableCreateLogMapper createLogMapper,
                                                     JobSchedulerInternalClient jobSchedulerInternalClient) {
        this.shardingProperties = shardingProperties;
        this.quarterResolver = quarterResolver;
        this.tableNameResolver = tableNameResolver;
        this.autoIncrementValueCalculator = autoIncrementValueCalculator;
        this.physicalTableMapper = physicalTableMapper;
        this.createLogMapper = createLogMapper;
        this.jobSchedulerInternalClient = jobSchedulerInternalClient;
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
                        .map(ShardingGovernanceConverter.INSTANCE::toPhysicalTableResponse)
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
        return ShardingGovernanceConverter.INSTANCE.toPhysicalTableResponse(entity);
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
                        .map(ShardingGovernanceConverter.INSTANCE::toCreateLogResponse)
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
        return ShardingGovernanceConverter.INSTANCE.toCreateLogResponse(entity);
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

    private List<String> resolvePhysicalTables(PaymentQuarterShardingProperties.TableRule rule) {
        return quarterResolver.quartersInRange(rule).stream()
                .map(quarter -> tableNameResolver.physicalTableName(rule, quarter))
                .toList();
    }

    private String resolvePhysicalTableName(PaymentQuarterShardingProperties.TableRule rule, ShardingQuarter quarter) {
        if (!quarterResolver.inRange(rule, quarter)) {
            return null;
        }
        return tableNameResolver.physicalTableName(rule, quarter);
    }

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

    private LambdaQueryWrapper<SysShardingTableCreateLogDO> buildCreateLogWrapper(ShardingTableCreateLogQueryRequest query) {
        return new LambdaQueryWrapper<SysShardingTableCreateLogDO>()
                .like(StringUtils.hasText(query.getBatchNo()), SysShardingTableCreateLogDO::getBatchNo, trim(query.getBatchNo()))
                .eq(StringUtils.hasText(query.getTriggerType()), SysShardingTableCreateLogDO::getTriggerType, trim(query.getTriggerType()))
                .eq(query.getDryRun() != null, SysShardingTableCreateLogDO::getDryRun, query.getDryRun())
                .eq(StringUtils.hasText(query.getRunStatus()), SysShardingTableCreateLogDO::getRunStatus, trim(query.getRunStatus()))
                .orderByDesc(SysShardingTableCreateLogDO::getCreateTime)
                .orderByDesc(SysShardingTableCreateLogDO::getId);
    }

    private ShardingTablePreCreateRemoteRequest toRemoteRequest(ShardingTableCreateRequest request,
                                                                boolean dryRun,
                                                                String operatorId,
                                                                String operatorName) {
        ShardingTableCreateRequest safeRequest = request == null ? new ShardingTableCreateRequest() : request;
        ShardingTablePreCreateRemoteRequest remoteRequest = ShardingGovernanceConverter.INSTANCE.toRemoteRequest(
                safeRequest,
                dryRun,
                operatorId,
                operatorName
        );
        remoteRequest.setLogicalTables(safeRequest.getLogicalTables() == null ? List.of() : safeRequest.getLogicalTables());
        return remoteRequest;
    }

    private ShardingTableCreateRequest copyRequest(ShardingTableCreateRequest request) {
        ShardingTableCreateRequest source = request == null ? new ShardingTableCreateRequest() : request;
        ShardingTableCreateRequest target = new ShardingTableCreateRequest();
        target.setIncludeCurrentQuarter(source.getIncludeCurrentQuarter());
        target.setIncludeNextQuarter(source.getIncludeNextQuarter());
        target.setLogicalTables(source.getLogicalTables() == null ? List.of() : source.getLogicalTables());
        target.setCompareSchemaIfExists(source.getCompareSchemaIfExists());
        return target;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
