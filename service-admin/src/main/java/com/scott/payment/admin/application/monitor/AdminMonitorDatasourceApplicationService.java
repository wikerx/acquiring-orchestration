package com.scott.payment.admin.application.monitor;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.ds.GroupDataSource;
import com.scott.payment.admin.config.MonitorDynamicDataSourceProperties;
import com.scott.payment.admin.dto.export.DataSourceMonitorExportRow;
import com.scott.payment.admin.dto.monitor.DataSourceMonitorResponse;
import com.scott.payment.component.db.sharding.PaymentOrderShardingAlgorithm;
import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementRange;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementValueCalculator;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorDatasourceApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Admin Monitor Datasource Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminMonitorDatasourceApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 动态数据源运行时入口。
     */
    private final DynamicRoutingDataSource dynamicRoutingDataSource;

    /**
     * 动态数据源静态配置快照。
     */
    private final MonitorDynamicDataSourceProperties monitorDynamicDataSourceProperties;

    /**
     * 分表配置快照。
     */
    private final PaymentQuarterShardingProperties paymentQuarterShardingProperties;

    /**
     * Spring 环境信息，用于读取当前激活 profile。
     */
    private final Environment environment;

    /**
     * Excel 导出服务。
     */
    private final ExcelExportService excelExportService;

    /**
     * Excel 国际化消息解析器。
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;

    /**
     * Excel 语言解析器。
     */
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 季度分表算法，用于推导物理表范围。
     */
    private final PaymentOrderShardingAlgorithm paymentOrderShardingAlgorithm = new PaymentOrderShardingAlgorithm();

    private final ShardingQuarterResolver shardingQuarterResolver = new ShardingQuarterResolver();

    private final ShardingPhysicalTableNameResolver shardingPhysicalTableNameResolver = new ShardingPhysicalTableNameResolver();

    private final ShardingAutoIncrementValueCalculator shardingAutoIncrementValueCalculator = new ShardingAutoIncrementValueCalculator();

    /**
     * 创建数据源监控应用服务。
     *
     * @param dynamicRoutingDataSourceProvider 动态数据源提供器
     * @param monitorDynamicDataSourceProperties 动态数据源静态配置
     * @param paymentQuarterShardingProperties 分表配置
     * @param environment Spring 环境对象
     * @param excelExportService Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化消息解析器
     * @param excelLocaleResolver Excel 语言解析器
     */
    public AdminMonitorDatasourceApplicationService(
            ObjectProvider<DynamicRoutingDataSource> dynamicRoutingDataSourceProvider,
            MonitorDynamicDataSourceProperties monitorDynamicDataSourceProperties,
            PaymentQuarterShardingProperties paymentQuarterShardingProperties,
            Environment environment,
            ExcelExportService excelExportService,
            ExcelI18nMessageResolver excelI18nMessageResolver,
            ExcelLocaleResolver excelLocaleResolver) {
        this.dynamicRoutingDataSource = dynamicRoutingDataSourceProvider.getIfAvailable();
        this.monitorDynamicDataSourceProperties = monitorDynamicDataSourceProperties;
        this.paymentQuarterShardingProperties = paymentQuarterShardingProperties;
        this.environment = environment;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 聚合数据源监控快照。
     *
     * <p>该方法会同时读取运行时已注册数据源、静态 datasource 配置和分表配置，
     * 最终输出一个前端可直接消费的监控快照对象。</p>
     *
     * @return 数据源监控响应
     */
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public DataSourceMonitorResponse snapshot() {
        DataSourceMonitorResponse response = new DataSourceMonitorResponse();
        Map<String, DataSource> runtimeDataSources = runtimeDataSources();
        Map<String, GroupDataSource> runtimeGroups = runtimeGroups();
        Map<String, List<String>> groupMembers = buildGroupMembers(runtimeGroups);

        response.setOverview(buildOverview(runtimeDataSources, runtimeGroups));
        response.setWarnings(buildWarnings(runtimeDataSources, groupMembers));
        response.setGroups(buildGroups(runtimeGroups, groupMembers));
        response.setDataSources(buildDataSourceItems(runtimeDataSources, groupMembers));
        response.setSharding(buildShardingSnapshot(runtimeDataSources, groupMembers));
        return response;
    }

    /**
     * 导出当前数据源监控快照。
     *
     * <p>导出内容来自实时快照，不读取业务表数据，因此不会影响分表数据路径。</p>
     *
     * @param operator 操作人
     * @param response HTTP 响应
     */
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void exportSnapshot(String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        DataSourceMonitorResponse snapshot = snapshot();
        List<DataSourceMonitorExportRow> rows = snapshot.getDataSources().stream()
                .map(dataSource -> toExportRow(dataSource, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<DataSourceMonitorExportRow>builder()
                        .fileName(excelI18nMessageResolver.resolve("excel.datasource.title", locale) + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(excelI18nMessageResolver.resolve("excel.datasource.title", locale))
                        .titleKey("excel.datasource.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                        .rowClass(DataSourceMonitorExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 转换数据源监控导出行。
     *
     * @param item 数据源运行明细
     * @param locale 当前语言
     * @return 导出行对象
     */
    private DataSourceMonitorExportRow toExportRow(DataSourceMonitorResponse.DataSourceItem item, Locale locale) {
        DataSourceMonitorExportRow row = new DataSourceMonitorExportRow();
        row.setDataSourceKey(item.getDataSourceKey());
        row.setGroupName(item.getGroupName());
        row.setRole(item.getRole());
        row.setPoolName(item.getPoolName());
        row.setDatabaseName(item.getDatabaseName());
        row.setJdbcUrl(item.getJdbcUrl());
        row.setRunning(formatBoolean(item.getRunning(), locale));
        row.setReachable(formatBoolean(item.getReachable(), locale));
        row.setActiveConnections(item.getActiveConnections());
        row.setIdleConnections(item.getIdleConnections());
        row.setTotalConnections(item.getTotalConnections());
        row.setThreadsAwaitingConnection(item.getThreadsAwaitingConnection());
        row.setMaximumPoolSize(item.getMaximumPoolSize());
        row.setMinimumIdle(item.getMinimumIdle());
        row.setRelatedShardingTables(String.join(",", item.getRelatedShardingTables()));
        return row;
    }

    /**
     * 格式化布尔值为 Excel 当前语言文案。
     *
     * @param value 布尔值
     * @param locale 当前语言
     * @return 当前语言文案
     */
    private String formatBoolean(Boolean value, Locale locale) {
        return Boolean.TRUE.equals(value)
                ? excelI18nMessageResolver.resolve("excel.common.yes", locale)
                : excelI18nMessageResolver.resolve("excel.common.no", locale);
    }

    /**
     * 构建监控总览。
     *
     * @param runtimeDataSources 已注册数据源
     * @param runtimeGroups 已注册分组
     * @return 总览对象
     */
    private DataSourceMonitorResponse.Overview buildOverview(Map<String, DataSource> runtimeDataSources,
                                                             Map<String, GroupDataSource> runtimeGroups) {
        DataSourceMonitorResponse.Overview overview = new DataSourceMonitorResponse.Overview();
        overview.setActiveProfile(resolveActiveProfile());
        overview.setPrimaryDataSource(monitorDynamicDataSourceProperties.getPrimary());
        overview.setStrictMode(Boolean.TRUE.equals(monitorDynamicDataSourceProperties.getStrict()));
        overview.setRoutingStrategyClassName(resolveRoutingStrategyClassName());
        overview.setRegisteredDataSourceCount(runtimeDataSources.size());
        overview.setRegisteredGroupCount(runtimeGroups.size());
        overview.setHealthyDataSourceCount(countHealthyDataSources(runtimeDataSources));
        overview.setShardingTableCount(paymentQuarterShardingProperties.getTables().size());
        return overview;
    }

    /**
     * 构建监控警告信息。
     *
     * @param runtimeDataSources 已注册数据源
     * @param groupMembers 分组成员映射
     * @return 告警列表
     */
    private List<String> buildWarnings(Map<String, DataSource> runtimeDataSources,
                                       Map<String, List<String>> groupMembers) {
        List<String> warnings = new ArrayList<>();
        if (dynamicRoutingDataSource == null) {
            warnings.add("当前服务未装配 DynamicRoutingDataSource，数据源监控只能显示静态配置。");
        }
        appendDuplicateJdbcWarnings(warnings);
        appendMissingShardingTargetWarnings(warnings, runtimeDataSources, groupMembers);
        appendUnavailableDataSourceWarnings(warnings, runtimeDataSources);
        return warnings;
    }

    /**
     * 构建动态数据源分组信息。
     *
     * @param runtimeGroups 分组对象
     * @param groupMembers 分组成员映射
     * @return 分组响应列表
     */
    private List<DataSourceMonitorResponse.GroupItem> buildGroups(Map<String, GroupDataSource> runtimeGroups,
                                                                  Map<String, List<String>> groupMembers) {
        List<DataSourceMonitorResponse.GroupItem> groups = new ArrayList<>();
        for (Map.Entry<String, GroupDataSource> entry : runtimeGroups.entrySet()) {
            DataSourceMonitorResponse.GroupItem item = new DataSourceMonitorResponse.GroupItem();
            item.setGroupName(entry.getKey());
            item.setStrategyClassName(entry.getValue().getDynamicDataSourceStrategy().getClass().getName());
            item.setMemberKeys(new ArrayList<>(groupMembers.getOrDefault(entry.getKey(), Collections.emptyList())));
            item.setMemberCount(item.getMemberKeys().size());
            groups.add(item);
        }
        return groups;
    }

    /**
     * 构建逐个数据源的运行明细。
     *
     * @param runtimeDataSources 已注册数据源
     * @param groupMembers 分组成员映射
     * @return 数据源明细列表
     */
    private List<DataSourceMonitorResponse.DataSourceItem> buildDataSourceItems(Map<String, DataSource> runtimeDataSources,
                                                                                Map<String, List<String>> groupMembers) {
        Map<String, List<String>> shardingTableBindings = buildShardingTableBindings(groupMembers);
        List<DataSourceMonitorResponse.DataSourceItem> items = new ArrayList<>();
        for (Map.Entry<String, DataSource> entry : runtimeDataSources.entrySet()) {
            String dataSourceKey = entry.getKey();
            DataSource dataSource = entry.getValue();
            DataSourceMonitorResponse.DataSourceItem item = new DataSourceMonitorResponse.DataSourceItem();
            item.setDataSourceKey(dataSourceKey);
            item.setGroupName(resolveGroupName(dataSourceKey, groupMembers));
            item.setRole(resolveDataSourceRole(dataSourceKey, item.getGroupName()));
            item.setDataSourceClassName(dataSource.getClass().getName());
            item.setPoolName(resolvePoolName(dataSourceKey, dataSource));
            item.setJdbcUrl(resolveJdbcUrl(dataSourceKey, dataSource));
            item.setDatabaseName(resolveDatabaseName(item.getJdbcUrl()));
            item.setRelatedShardingTables(new ArrayList<>(shardingTableBindings.getOrDefault(dataSourceKey, Collections.emptyList())));
            populateRuntimeMetrics(item, dataSource);
            items.add(item);
        }
        return items;
    }

    /**
     * 构建分表规则快照。
     *
     * @param runtimeDataSources 已注册数据源
     * @param groupMembers 分组成员映射
     * @return 分表快照
     */
    private DataSourceMonitorResponse.ShardingSnapshot buildShardingSnapshot(Map<String, DataSource> runtimeDataSources,
                                                                             Map<String, List<String>> groupMembers) {
        DataSourceMonitorResponse.ShardingSnapshot snapshot = new DataSourceMonitorResponse.ShardingSnapshot();
        snapshot.setStrategy(paymentQuarterShardingProperties.getStrategy());
        snapshot.setDatabaseTimezone(paymentQuarterShardingProperties.getDatabaseTimezone());
        snapshot.setShardingColumn(paymentQuarterShardingProperties.getShardingColumn());
        snapshot.setDdlDataSource(paymentQuarterShardingProperties.getTableMaintenance().getDdlDataSource());
        snapshot.setAllowCreateFromTemplateTable(paymentQuarterShardingProperties.getTableMaintenance().getAllowCreateFromTemplateTable());
        snapshot.setAllowAlterExistingTable(paymentQuarterShardingProperties.getTableMaintenance().getAllowAlterExistingTable());
        snapshot.setSetAutoIncrementStartValue(paymentQuarterShardingProperties.getTableMaintenance().getSetAutoIncrementStartValue());

        ShardingQuarter currentQuarter = shardingQuarterResolver.currentQuarter(paymentQuarterShardingProperties);
        ShardingAutoIncrementRange currentRange = shardingAutoIncrementValueCalculator.calculate(paymentQuarterShardingProperties, currentQuarter);

        List<DataSourceMonitorResponse.ShardingRuleItem> tableRules = new ArrayList<>();
        for (Map.Entry<String, PaymentQuarterShardingProperties.TableRule> entry : paymentQuarterShardingProperties.getTables().entrySet()) {
            PaymentQuarterShardingProperties.TableRule rule = entry.getValue();
            DataSourceMonitorResponse.ShardingRuleItem item = new DataSourceMonitorResponse.ShardingRuleItem();
            item.setRuleKey(entry.getKey());
            item.setLogicalTable(rule.getLogicalTable());
            item.setEnabled(Boolean.TRUE.equals(rule.getEnabled()));
            item.setTemplateTable(rule.getTemplateTable());
            item.setIdColumn(rule.getIdColumn());
            item.setShardingColumn(rule.getShardingColumn());
            item.setActualDataSource(rule.getActualDataSource());
            item.setDescription(rule.getDescription());
            item.setStartYear(rule.getStartYear());
            item.setStartQuarter(rule.getStartQuarter());
            item.setEndYear(rule.getEndYear());
            item.setEndQuarter(rule.getEndQuarter());
            item.setTableNameFormat(rule.getTableNameFormat());
            item.setCurrentPhysicalTable(resolvePhysicalTableName(rule, currentQuarter));
            item.setNextPhysicalTable(resolvePhysicalTableName(rule, currentQuarter.next()));
            item.setCurrentQuarterAutoIncrementStart(currentRange.startValue());
            item.setCurrentQuarterAutoIncrementMax(currentRange.maxValue());
            item.setActualTargetType(resolveActualTargetType(rule.getActualDataSource(), runtimeDataSources, groupMembers));
            item.setActualTargetMembers(resolveActualTargetMembers(rule.getActualDataSource(), groupMembers));

            List<String> physicalTables = paymentOrderShardingAlgorithm.physicalTables(paymentQuarterShardingProperties, entry.getKey());
            item.setPhysicalTables(physicalTables);
            item.setPhysicalTableCount(physicalTables.size());
            item.setFirstPhysicalTable(physicalTables.isEmpty() ? null : physicalTables.get(0));
            item.setLastPhysicalTable(physicalTables.isEmpty() ? null : physicalTables.get(physicalTables.size() - 1));
            tableRules.add(item);
        }
        snapshot.setTables(tableRules);
        return snapshot;
    }

    private String resolvePhysicalTableName(PaymentQuarterShardingProperties.TableRule rule, ShardingQuarter quarter) {
        if (!shardingQuarterResolver.inRange(rule, quarter)) {
            return null;
        }
        return shardingPhysicalTableNameResolver.physicalTableName(rule, quarter);
    }

    /**
     * 统计当前可探测成功的数据源数量。
     *
     * @param runtimeDataSources 已注册数据源
     * @return 可用数据源数量
     */
    private int countHealthyDataSources(Map<String, DataSource> runtimeDataSources) {
        int count = 0;
        for (DataSource dataSource : runtimeDataSources.values()) {
            if (probeReachable(dataSource)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 追加 JDBC 地址重复告警，帮助识别开发环境中的“逻辑主从、物理同库”场景。
     *
     * @param warnings 告警列表
     */
    private void appendDuplicateJdbcWarnings(List<String> warnings) {
        Map<String, List<String>> sameJdbcDataSources = monitorDynamicDataSourceProperties.getDatasource().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().getUrl() != null)
                .collect(Collectors.groupingBy(
                        entry -> sanitizeJdbcUrl(entry.getValue().getUrl()),
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));
        for (Map.Entry<String, List<String>> entry : sameJdbcDataSources.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            warnings.add("以下数据源当前指向同一个物理 JDBC 地址：" + String.join(", ", entry.getValue())
                    + " -> " + entry.getKey()
                    + "。这通常表示开发环境仅完成了逻辑读写分离，尚未接入真实物理从库。");
        }
    }

    /**
     * 追加分表目标缺失告警，避免页面只展示规则却无法识别真实数据源。
     *
     * @param warnings 告警列表
     * @param runtimeDataSources 已注册数据源
     * @param groupMembers 分组成员映射
     */
    private void appendMissingShardingTargetWarnings(List<String> warnings,
                                                     Map<String, DataSource> runtimeDataSources,
                                                     Map<String, List<String>> groupMembers) {
        Set<String> dataSourceKeys = runtimeDataSources.keySet();
        Set<String> groupNames = groupMembers.keySet();
        for (Map.Entry<String, PaymentQuarterShardingProperties.TableRule> entry : paymentQuarterShardingProperties.getTables().entrySet()) {
            String actualDataSource = entry.getValue().getActualDataSource();
            if (actualDataSource == null) {
                warnings.add("分表规则 " + entry.getKey() + " 未配置 actual-data-source，页面无法判断物理路由归属。");
                continue;
            }
            if (!dataSourceKeys.contains(actualDataSource) && !groupNames.contains(actualDataSource)) {
                warnings.add("分表规则 " + entry.getKey() + " 的 actual-data-source=" + actualDataSource
                        + " 当前未在动态数据源中注册，请检查 sharding 与 datasource 配置是否一致。");
            }
        }
    }

    /**
     * 追加不可达数据源告警。
     *
     * @param warnings 告警列表
     * @param runtimeDataSources 已注册数据源
     */
    private void appendUnavailableDataSourceWarnings(List<String> warnings,
                                                     Map<String, DataSource> runtimeDataSources) {
        for (Map.Entry<String, DataSource> entry : runtimeDataSources.entrySet()) {
            if (probeReachable(entry.getValue())) {
                continue;
            }
            warnings.add("数据源 " + entry.getKey() + " 当前连接探测失败，请检查数据库可达性、账号口令和连接池状态。");
        }
    }

    /**
     * 读取运行时数据源映射。
     *
     * @return 数据源名称到数据源对象的映射
     */
    private Map<String, DataSource> runtimeDataSources() {
        if (dynamicRoutingDataSource == null) {
            return Collections.emptyMap();
        }
        return dynamicRoutingDataSource.getDataSources();
    }

    /**
     * 读取运行时分组映射。
     *
     * @return 分组名称到分组对象的映射
     */
    private Map<String, GroupDataSource> runtimeGroups() {
        if (dynamicRoutingDataSource == null) {
            return Collections.emptyMap();
        }
        return dynamicRoutingDataSource.getGroupDataSources();
    }

    /**
     * 根据运行时分组构建分组成员索引。
     *
     * @param runtimeGroups 分组对象
     * @return 分组成员映射
     */
    private Map<String, List<String>> buildGroupMembers(Map<String, GroupDataSource> runtimeGroups) {
        Map<String, List<String>> groupMembers = new LinkedHashMap<>();
        for (Map.Entry<String, GroupDataSource> entry : runtimeGroups.entrySet()) {
            List<String> members = new ArrayList<>(entry.getValue().getDataSourceMap().keySet());
            Collections.sort(members);
            groupMembers.put(entry.getKey(), members);
        }
        return groupMembers;
    }

    /**
     * 构建分表表与数据源的绑定关系。
     *
     * @param groupMembers 分组成员映射
     * @return 数据源名称到逻辑表列表的映射
     */
    private Map<String, List<String>> buildShardingTableBindings(Map<String, List<String>> groupMembers) {
        Map<String, List<String>> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, PaymentQuarterShardingProperties.TableRule> entry : paymentQuarterShardingProperties.getTables().entrySet()) {
            PaymentQuarterShardingProperties.TableRule rule = entry.getValue();
            if (!Boolean.TRUE.equals(rule.getEnabled()) || rule.getActualDataSource() == null) {
                continue;
            }
            List<String> targetDataSources = resolveActualTargetMembers(rule.getActualDataSource(), groupMembers);
            for (String dataSourceKey : targetDataSources) {
                bindings.computeIfAbsent(dataSourceKey, key -> new ArrayList<>()).add(rule.getLogicalTable());
            }
        }
        return bindings;
    }

    /**
     * 填充运行时连接池指标。
     *
     * @param item 数据源明细
     * @param dataSource 数据源对象
     */
    private void populateRuntimeMetrics(DataSourceMonitorResponse.DataSourceItem item, DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            item.setRunning(hikariDataSource.isRunning());
            item.setReachable(probeReachable(hikariDataSource));
            item.setReachabilityMessage(Boolean.TRUE.equals(item.getReachable()) ? "OK" : "FAILED");

            HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
            if (poolMxBean != null) {
                item.setActiveConnections(poolMxBean.getActiveConnections());
                item.setIdleConnections(poolMxBean.getIdleConnections());
                item.setTotalConnections(poolMxBean.getTotalConnections());
                item.setThreadsAwaitingConnection(poolMxBean.getThreadsAwaitingConnection());
            }

            HikariConfigMXBean configMxBean = hikariDataSource.getHikariConfigMXBean();
            if (configMxBean != null) {
                item.setMaximumPoolSize(configMxBean.getMaximumPoolSize());
                item.setMinimumIdle(configMxBean.getMinimumIdle());
                item.setConnectionTimeoutMs(configMxBean.getConnectionTimeout());
                item.setIdleTimeoutMs(configMxBean.getIdleTimeout());
                item.setMaxLifetimeMs(configMxBean.getMaxLifetime());
            }
            return;
        }
        item.setRunning(Boolean.TRUE);
        item.setReachable(probeReachable(dataSource));
        item.setReachabilityMessage(Boolean.TRUE.equals(item.getReachable()) ? "OK" : "FAILED");
    }

    /**
     * 通过短连接探测数据源可用性。
     *
     * @param dataSource 数据源对象
     * @return 是否可用
     */
    private boolean probeReachable(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException exception) {
            return false;
        }
    }

    /**
     * 解析当前激活环境。
     *
     * @return 当前 profile
     */
    private String resolveActiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return "default";
        }
        return String.join(",", activeProfiles);
    }

    /**
     * 解析组内路由策略类名。
     *
     * @return 策略类名
     */
    private String resolveRoutingStrategyClassName() {
        if (monitorDynamicDataSourceProperties.getStrategy() != null) {
            return monitorDynamicDataSourceProperties.getStrategy().getName();
        }
        return "N/A";
    }

    /**
     * 解析数据源所属分组。
     *
     * @param dataSourceKey 数据源名称
     * @param groupMembers 分组成员映射
     * @return 分组名称
     */
    private String resolveGroupName(String dataSourceKey, Map<String, List<String>> groupMembers) {
        for (Map.Entry<String, List<String>> entry : groupMembers.entrySet()) {
            if (entry.getValue().contains(dataSourceKey)) {
                return entry.getKey();
            }
        }
        return dataSourceKey;
    }

    /**
     * 解析数据源角色文本。
     *
     * @param dataSourceKey 数据源名称
     * @param groupName 分组名称
     * @return 角色文本
     */
    private String resolveDataSourceRole(String dataSourceKey, String groupName) {
        if (Objects.equals(dataSourceKey, monitorDynamicDataSourceProperties.getPrimary())) {
            return "PRIMARY";
        }
        if (!Objects.equals(dataSourceKey, groupName)) {
            return "GROUP_MEMBER";
        }
        return "SINGLE";
    }

    /**
     * 解析连接池名称。
     *
     * @param dataSourceKey 数据源名称
     * @param dataSource 数据源对象
     * @return 连接池名称
     */
    private String resolvePoolName(String dataSourceKey, DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            return hikariDataSource.getPoolName();
        }
        DataSourceProperty dataSourceProperty = monitorDynamicDataSourceProperties.getDatasource().get(dataSourceKey);
        return dataSourceProperty == null ? dataSourceKey : dataSourceProperty.getPoolName();
    }

    /**
     * 解析并脱敏 JDBC 地址。
     *
     * @param dataSourceKey 数据源名称
     * @param dataSource 数据源对象
     * @return 脱敏后的 JDBC 地址
     */
    private String resolveJdbcUrl(String dataSourceKey, DataSource dataSource) {
        String jdbcUrl = null;
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            jdbcUrl = hikariDataSource.getJdbcUrl();
        }
        if (jdbcUrl == null) {
            DataSourceProperty dataSourceProperty = monitorDynamicDataSourceProperties.getDatasource().get(dataSourceKey);
            jdbcUrl = dataSourceProperty == null ? null : dataSourceProperty.getUrl();
        }
        return sanitizeJdbcUrl(jdbcUrl);
    }

    /**
     * 对 JDBC 地址做展示级裁剪，只保留主机、端口和库名。
     *
     * @param jdbcUrl JDBC 原始地址
     * @return 裁剪后的地址
     */
    private String sanitizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "N/A";
        }
        int queryIndex = jdbcUrl.indexOf('?');
        return queryIndex < 0 ? jdbcUrl : jdbcUrl.substring(0, queryIndex);
    }

    /**
     * 从 JDBC 地址中提取数据库名称。
     *
     * @param jdbcUrl JDBC 地址
     * @return 数据库名称
     */
    private String resolveDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null || "N/A".equals(jdbcUrl)) {
            return "N/A";
        }
        int slashIndex = jdbcUrl.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == jdbcUrl.length() - 1) {
            return "N/A";
        }
        return jdbcUrl.substring(slashIndex + 1);
    }

    /**
     * 解析分表目标类型。
     *
     * @param actualDataSource 目标数据源或分组名
     * @param runtimeDataSources 已注册数据源
     * @param groupMembers 分组成员映射
     * @return 目标类型
     */
    private String resolveActualTargetType(String actualDataSource,
                                           Map<String, DataSource> runtimeDataSources,
                                           Map<String, List<String>> groupMembers) {
        if (actualDataSource == null) {
            return "UNKNOWN";
        }
        if (runtimeDataSources.containsKey(actualDataSource)) {
            return "DATASOURCE";
        }
        if (groupMembers.containsKey(actualDataSource)) {
            return "GROUP";
        }
        return "UNKNOWN";
    }

    /**
     * 解析分表规则最终会落到哪些物理数据源。
     *
     * @param actualDataSource 目标数据源或分组名
     * @param groupMembers 分组成员映射
     * @return 物理数据源列表
     */
    private List<String> resolveActualTargetMembers(String actualDataSource,
                                                    Map<String, List<String>> groupMembers) {
        if (actualDataSource == null) {
            return Collections.emptyList();
        }
        if (groupMembers.containsKey(actualDataSource)) {
            return new ArrayList<>(groupMembers.get(actualDataSource));
        }
        return Collections.singletonList(actualDataSource);
    }
}
