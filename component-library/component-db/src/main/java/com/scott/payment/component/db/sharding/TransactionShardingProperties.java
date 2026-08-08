package com.scott.payment.component.db.sharding;

import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingProperties
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 交易 ShardingSphere 规则元数据，只保存逻辑拓扑、已验证节点、版本和查询预算，不承载 JDBC 凭证。
 * @status : create
 */
@ConfigurationProperties(prefix = "transaction-sharding")
public class TransactionShardingProperties {

    /** 唯一允许的数据库路由时区，避免 JVM 所在地区改变季度归属。 */
    public static final String REQUIRED_ZONE_ID = "Asia/Shanghai";
    /** 所有交易逻辑表统一使用的分片键。 */
    public static final String REQUIRED_SHARDING_COLUMN = "transaction_date_time";
    /** 当前正式交易逻辑表数量。 */
    public static final int FORMAL_LOGIC_TABLE_COUNT = 24;
    /** 仅在卡资料能力启用后才必须加入活动分片规则的逻辑表。 */
    public static final String CARD_VAULT_LOGIC_TABLE = "transaction_card_vault";

    /** 版本化规则标识，未发布配置不得激活复合数据源。 */
    private String ruleVersion = "unpublished";
    /** 对路由相关字段计算的 SHA-256，用于阻止半份或漂移配置启动。 */
    private String ruleChecksum;
    /** 交易时间解释时区，固定为 Asia/Shanghai。 */
    private String databaseZoneId = REQUIRED_ZONE_ID;
    /** 交易表分片列，固定为 transaction_date_time。 */
    private String shardingColumn = REQUIRED_SHARDING_COLUMN;
    /** 读写分离组中的唯一写库数据源名。 */
    private String primaryDataSource = DataSourceName.MASTER;
    /** 可承接普通读请求的副本数据源名，不包含连接信息。 */
    private List<String> replicaDataSources = new ArrayList<>(List.of(DataSourceName.SLAVE_1, DataSourceName.SLAVE_2));
    /** 已建表且通过 schema、字符集和号段校验的季度后缀集合。 */
    private List<String> physicalNodes = new ArrayList<>();
    /** 必须完整匹配包含卡资料表的 24 表正式拓扑。 */
    private List<String> logicTables = new ArrayList<>(defaultLogicTables());
    /** 允许直接选择 transaction 逻辑数据源的服务白名单。 */
    private List<String> directAccessServices = new ArrayList<>(List.of(
            "service-payment", "service-admin", "service-merchant", "service-risk", "service-data"));
    /** 跨季度查询的超时、结果行数和导出并发预算。 */
    private QueryBudget queryBudget = new QueryBudget();

    /**
     * 返回必须被同一规则版本完整接管的交易表集合。
     *
     * @return 不可变的 24 张逻辑表名
     */
    public static List<String> defaultLogicTables() {
        return List.of(
                "transaction_order",
                "transaction_operation",
                "transaction_merchant_snapshot",
                "transaction_payment_method_info",
                "transaction_payer_info",
                "transaction_billing_info",
                "transaction_additional_info",
                "transaction_authentication_info",
                "transaction_product_item",
                "transaction_channel_request",
                "transaction_channel_interaction_log",
                "transaction_channel_callback",
                "transaction_channel_callback_log",
                "transaction_flow_event",
                "transaction_status_history",
                "transaction_amount_change_log",
                "transaction_finance_state",
                "transaction_currency_conversion",
                "transaction_merchant_notification",
                "transaction_merchant_notification_log",
                "transaction_merchant_api_interaction_log",
                "transaction_event_outbox",
                "transaction_abnormal_event",
                CARD_VAULT_LOGIC_TABLE);
    }

    /**
     * 返回卡资料表上线前已发布的 23 张交易逻辑表基线。
     *
     * @return 不含卡资料表的不可变逻辑表集合
     */
    public static List<String> previousLogicTables() {
        return defaultLogicTables().stream()
                .filter(table -> !CARD_VAULT_LOGIC_TABLE.equals(table))
                .toList();
    }

    /**
     * 在注册 transaction 数据源前校验固定时区、分片键、表集合、物理节点和 checksum。
     *
     * @throws IllegalStateException 任一发布字段缺失、漂移或不完整时抛出
     */
    public void validateForActivation() {
        if (!REQUIRED_ZONE_ID.equals(databaseZoneId)) {
            throw new IllegalStateException("transaction sharding database zone must be Asia/Shanghai");
        }
        if (!REQUIRED_SHARDING_COLUMN.equals(shardingColumn)) {
            throw new IllegalStateException("transaction sharding column must be transaction_date_time");
        }
        if (ruleVersion == null || ruleVersion.isBlank() || "unpublished".equals(ruleVersion)) {
            throw new IllegalStateException("transaction sharding rule version must be published before activation");
        }
        if (physicalNodes == null || physicalNodes.isEmpty()) {
            throw new IllegalStateException("transaction sharding physical nodes must contain verified existing quarters");
        }
        if (physicalNodes.stream().anyMatch(node -> node == null || !node.matches("\\d{4}0[1-4]"))
                || physicalNodes.stream().distinct().count() != physicalNodes.size()) {
            throw new IllegalStateException("transaction sharding physical nodes must be unique yyyyQQ suffixes");
        }
        if (!matchesPublishedLogicTableTopology()) {
            throw new IllegalStateException("transaction sharding rules must contain exactly 24 formal logic tables");
        }
        validateQueryBudget();
        String calculated = TransactionShardingRuleChecksum.calculate(this);
        if (!calculated.equalsIgnoreCase(ruleChecksum)) {
            throw new IllegalStateException("transaction sharding rule checksum mismatch for version " + ruleVersion);
        }
    }

    /** @return 当前规则版本 */
    public String getRuleVersion() {
        return ruleVersion;
    }

    /** @param ruleVersion 待装载的版本化规则标识 */
    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    /** @return 当前规则声明的 SHA-256 校验和 */
    public String getRuleChecksum() {
        return ruleChecksum;
    }

    /** @param ruleChecksum 发布流程计算并写入的规则校验和 */
    public void setRuleChecksum(String ruleChecksum) {
        this.ruleChecksum = ruleChecksum;
    }

    /** @return 数据库交易时间解释时区 */
    public String getDatabaseZoneId() {
        return databaseZoneId;
    }

    /** @param databaseZoneId 数据库交易时间解释时区，只允许 Asia/Shanghai */
    public void setDatabaseZoneId(String databaseZoneId) {
        this.databaseZoneId = databaseZoneId;
    }

    /** @return 统一交易分片列 */
    public String getShardingColumn() {
        return shardingColumn;
    }

    /** @param shardingColumn 分片列，只允许 transaction_date_time */
    public void setShardingColumn(String shardingColumn) {
        this.shardingColumn = shardingColumn;
    }

    /** @return 写库数据源注册名 */
    public String getPrimaryDataSource() {
        return primaryDataSource;
    }

    /** @param primaryDataSource 写库数据源注册名，不得包含 JDBC URL */
    public void setPrimaryDataSource(String primaryDataSource) {
        this.primaryDataSource = primaryDataSource;
    }

    /** @return 普通读可用的副本数据源注册名 */
    public List<String> getReplicaDataSources() {
        return replicaDataSources;
    }

    /** @param replicaDataSources 副本数据源注册名，null 视为空集合 */
    public void setReplicaDataSources(List<String> replicaDataSources) {
        this.replicaDataSources = replicaDataSources == null ? new ArrayList<>() : new ArrayList<>(replicaDataSources);
    }

    /** @return 已验证并可发布的季度后缀 */
    public List<String> getPhysicalNodes() {
        return physicalNodes;
    }

    /** @param physicalNodes 已建且校验通过的季度后缀，null 视为空集合 */
    public void setPhysicalNodes(List<String> physicalNodes) {
        this.physicalNodes = physicalNodes == null ? new ArrayList<>() : new ArrayList<>(physicalNodes);
    }

    /** @return 当前规则覆盖的正式交易逻辑表 */
    public List<String> getLogicTables() {
        return logicTables;
    }

    /** @param logicTables 包含卡资料表的 24 表正式拓扑 */
    public void setLogicTables(List<String> logicTables) {
        this.logicTables = logicTables == null ? new ArrayList<>() : new ArrayList<>(logicTables);
    }

    /** 只允许完整正式集合，防止缺表或未知表配置被激活。 */
    private boolean matchesPublishedLogicTableTopology() {
        if (logicTables == null || logicTables.stream().distinct().count() != logicTables.size()) {
            return false;
        }
        return logicTables.size() == FORMAL_LOGIC_TABLE_COUNT
                && logicTables.containsAll(defaultLogicTables());
    }

    /** @return 允许直接访问 transaction 数据源的服务名 */
    public List<String> getDirectAccessServices() {
        return directAccessServices;
    }

    /** @param directAccessServices 交易直连服务白名单，null 视为空集合 */
    public void setDirectAccessServices(List<String> directAccessServices) {
        this.directAccessServices = directAccessServices == null ? new ArrayList<>() : new ArrayList<>(directAccessServices);
    }

    /** @return 同步查询和导出资源预算 */
    public QueryBudget getQueryBudget() {
        return queryBudget;
    }

    /** @param queryBudget 查询资源预算，null 恢复默认值 */
    public void setQueryBudget(QueryBudget queryBudget) {
        this.queryBudget = queryBudget == null ? new QueryBudget() : queryBudget;
    }

    /** 拒绝无法实际执行的查询超时、结果行数和并发预算。 */
    private void validateQueryBudget() {
        if (queryBudget.getSynchronousTimeoutMillis() <= 0L
                || queryBudget.getSynchronousTimeoutMillis() > Integer.MAX_VALUE * 1000L) {
            throw new IllegalStateException("transaction query synchronous timeout is invalid");
        }
        if (queryBudget.getMaxResultRows() <= 0) {
            throw new IllegalStateException("transaction query max result rows must be positive");
        }
        if (queryBudget.getMaxConcurrentExportsPerUser() <= 0) {
            throw new IllegalStateException("transaction export concurrency must be positive");
        }
    }

    /**
     * 同步查询资源预算。它限制资源消耗，但不恢复历史季度跨度限制。
     */
    public static class QueryBudget {
        /** 单次同步交易查询允许占用的最长毫秒数。 */
        private long synchronousTimeoutMillis = 5000;
        /** 单次同步查询允许返回的最大记录数。 */
        private int maxResultRows = 1000;
        /** 单一用户同时执行跨季度导出的最大任务数。 */
        private int maxConcurrentExportsPerUser = 1;

        /** @return 同步查询超时毫秒数 */
        public long getSynchronousTimeoutMillis() {
            return synchronousTimeoutMillis;
        }

        /** @param synchronousTimeoutMillis 同步查询超时毫秒数 */
        public void setSynchronousTimeoutMillis(long synchronousTimeoutMillis) {
            this.synchronousTimeoutMillis = synchronousTimeoutMillis;
        }

        /** @return 同步查询最大结果行数 */
        public int getMaxResultRows() {
            return maxResultRows;
        }

        /** @param maxResultRows 同步查询最大结果行数 */
        public void setMaxResultRows(int maxResultRows) {
            this.maxResultRows = maxResultRows;
        }

        /** @return 单用户最大并发导出数 */
        public int getMaxConcurrentExportsPerUser() {
            return maxConcurrentExportsPerUser;
        }

        /** @param maxConcurrentExportsPerUser 单用户最大并发导出数 */
        public void setMaxConcurrentExportsPerUser(int maxConcurrentExportsPerUser) {
            this.maxConcurrentExportsPerUser = maxConcurrentExportsPerUser;
        }
    }
}
