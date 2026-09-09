package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceMonitorResponse
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : data来源监控响应模型，位于 运营后台服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
 * @status : create
 */
@Data
public class DataSourceMonitorResponse {

    /**
     * 环境摘要信息。
     */
    private Overview overview;

    /**
     * 需要重点关注的提示信息。
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 动态数据源分组信息。
     */
    private List<GroupItem> groups = new ArrayList<>();

    /**
     * 逐个数据源的运行明细。
     */
    private List<DataSourceItem> dataSources = new ArrayList<>();

    /**
     * 分表规则与物理表范围信息。
     */
    private ShardingSnapshot sharding;

    /**
     * 数据源监控总览。
     */
    @Data
    public static class Overview {

        /**
         * 当前激活环境。
         */
        private String activeProfile;

        /**
         * 默认数据源名称。
         */
        private String primaryDataSource;

        /**
         * 严格模式开关。
         */
        private Boolean strictMode;

        /**
         * 分组路由策略类名。
         */
        private String routingStrategyClassName;

        /**
         * 已注册数据源数量。
         */
        private Integer registeredDataSourceCount;

        /**
         * 已注册分组数量。
         */
        private Integer registeredGroupCount;

        /**
         * 当前可用数据源数量。
         */
        private Integer healthyDataSourceCount;

        /**
         * 当前分表逻辑表数量。
         */
        private Integer shardingTableCount;
    }

    /**
     * 动态数据源分组信息。
     */
    @Data
    public static class GroupItem {

        /**
         * 分组名称，例如 slave。
         */
        private String groupName;

        /**
         * 组内路由策略类名。
         */
        private String strategyClassName;

        /**
         * 分组成员数量。
         */
        private Integer memberCount;

        /**
         * 分组成员数据源名称列表。
         */
        private List<String> memberKeys = new ArrayList<>();
    }

    /**
     * 单个数据源运行明细。
     */
    @Data
    public static class DataSourceItem {

        /**
         * 数据源名称，例如 master、slave_1。
         */
        private String dataSourceKey;

        /**
         * 所属分组名称；独立数据源时与自身一致。
         */
        private String groupName;

        /**
         * 展示角色，例如 PRIMARY、GROUP_MEMBER。
         */
        private String role;

        /**
         * 数据源实现类名。
         */
        private String dataSourceClassName;

        /**
         * 连接池名称。
         */
        private String poolName;

        /**
         * 脱敏后的 JDBC 地址。
         */
        private String jdbcUrl;

        /**
         * 提取出的数据库名称，便于运维快速识别。
         */
        private String databaseName;

        /**
         * 连接池运行状态。
         */
        private Boolean running;

        /**
         * 最近一次探测是否成功。
         */
        private Boolean reachable;

        /**
         * 探测结果说明。
         */
        private String reachabilityMessage;

        /**
         * 当前活跃连接数。
         */
        private Integer activeConnections;

        /**
         * 当前空闲连接数。
         */
        private Integer idleConnections;

        /**
         * 当前连接池总连接数。
         */
        private Integer totalConnections;

        /**
         * 当前等待连接线程数。
         */
        private Integer threadsAwaitingConnection;

        /**
         * 连接池最大连接数。
         */
        private Integer maximumPoolSize;

        /**
         * 连接池最小空闲数。
         */
        private Integer minimumIdle;

        /**
         * 获取连接超时时间，单位毫秒。
         */
        private Long connectionTimeoutMs;

        /**
         * 空闲超时时间，单位毫秒。
         */
        private Long idleTimeoutMs;

        /**
         * 连接最大生命周期，单位毫秒。
         */
        private Long maxLifetimeMs;

        /**
         * 明确绑定到该数据源的分表逻辑表。
         */
        private List<String> relatedShardingTables = new ArrayList<>();
    }

    /**
     * 分表监控快照。
     */
    @Data
    public static class ShardingSnapshot {

        /**
         * 分表策略标识。
         */
        private String strategy;

        /**
         * 分表统一时区。
         */
        private String databaseTimezone;

        /**
         * 默认分表字段。
         */
        private String shardingColumn;

        /**
         * DDL 使用的数据源，必须保持为主库。
         */
        private String ddlDataSource;

        /**
         * 是否允许从模板表创建物理表。
         */
        private Boolean allowCreateFromTemplateTable;

        /**
         * 是否允许自动修改已存在表结构。
         */
        private Boolean allowAlterExistingTable;

        /**
         * 是否配置新表 AUTO_INCREMENT 起始值。
         */
        private Boolean setAutoIncrementStartValue;

        /**
         * 分表规则列表。
         */
        private List<ShardingRuleItem> tables = new ArrayList<>();
    }

    /**
     * 单张逻辑表的分表规则。
     */
    @Data
    public static class ShardingRuleItem {

        /**
         * 分表规则键。
         */
        private String ruleKey;

        /**
         * 逻辑表名。
         */
        private String logicalTable;

        /**
         * 是否启用分表。
         */
        private Boolean enabled;

        /**
         * 模板表名。
         */
        private String templateTable;

        /**
         * 自增主键字段名。
         */
        private String idColumn;

        /**
         * 当前逻辑表使用的分表字段。
         */
        private String shardingColumn;

        /**
         * 实际物理数据源或分组名。
         */
        private String actualDataSource;

        /**
         * 目标归属类型：DATASOURCE、GROUP、UNKNOWN。
         */
        private String actualTargetType;

        /**
         * 若目标是分组，则展示该组成员；若目标是单数据源，则展示单元素列表。
         */
        private List<String> actualTargetMembers = new ArrayList<>();

        /**
         * 当前规则对应的说明。
         */
        private String description;

        /**
         * 起始年份。
         */
        private Integer startYear;

        /**
         * 起始季度。
         */
        private Integer startQuarter;

        /**
         * 结束年份。
         */
        private Integer endYear;

        /**
         * 结束季度。
         */
        private Integer endQuarter;

        /**
         * 物理表命名格式。
         */
        private String tableNameFormat;

        /**
         * 当前季度物理表名。
         */
        private String currentPhysicalTable;

        /**
         * 下一季度物理表名。
         */
        private String nextPhysicalTable;

        /**
         * 当前季度 AUTO_INCREMENT 起始值。
         */
        private Long currentQuarterAutoIncrementStart;

        /**
         * 当前季度 AUTO_INCREMENT 最大安全值。
         */
        private Long currentQuarterAutoIncrementMax;

        /**
         * 当前规则推导出的物理表数量。
         */
        private Integer physicalTableCount;

        /**
         * 当前规则推导出的首张物理表。
         */
        private String firstPhysicalTable;

        /**
         * 当前规则推导出的末张物理表。
         */
        private String lastPhysicalTable;

        /**
         * 当前规则推导出的完整物理表名列表。
         */
        private List<String> physicalTables = new ArrayList<>();
    }
}
