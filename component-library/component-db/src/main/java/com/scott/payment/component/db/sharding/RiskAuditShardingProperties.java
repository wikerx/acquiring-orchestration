package com.scott.payment.component.db.sharding;

import com.scott.payment.component.db.constant.DataSourceName;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 风控审计季度分片运行配置，只描述逻辑表、已验收物理节点和允许接入的服务，
 * 不保存数据库连接凭证或风控敏感数据。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "risk-audit-sharding")
public class RiskAuditShardingProperties {

    /**
     * 风控评估主记录逻辑表。
     */
    public static final String EVALUATION_RECORD_TABLE = "risk_evaluation_record";

    /**
     * 风控规则执行明细逻辑表。
     */
    public static final String EVALUATION_DETAIL_TABLE = "risk_evaluation_hit_detail";

    /**
     * 风控记录号到评估季度的非分片定位表。
     */
    public static final String EVALUATION_LOCATOR_TABLE = "risk_evaluation_locator";

    /**
     * 风控审计统一季度路由字段。
     */
    public static final String REQUIRED_SHARDING_COLUMN = "evaluation_time";

    /**
     * 未完成物理表迁移和配置发布时保持关闭，关闭状态由 riskAudit 别名兼容访问原单表。
     */
    private boolean enabled;

    /**
     * 当前已发布风控审计分片规则版本。
     */
    private String ruleVersion = "unpublished";

    /**
     * 对路由关键字段计算的 SHA-256 校验和。
     */
    private String ruleChecksum;

    /**
     * 评估时间和季度边界的数据库解释时区。
     */
    private String databaseZoneId = TransactionShardingProperties.REQUIRED_ZONE_ID;

    /**
     * 两张风控审计表共同使用的季度路由字段。
     */
    private String shardingColumn = REQUIRED_SHARDING_COLUMN;

    /**
     * 是否输出 ShardingSphere 实际 SQL；默认关闭以避免审计条件进入日志。
     */
    private boolean sqlShow;

    /**
     * 风控审计写入使用的主数据源名称。
     */
    private String primaryDataSource = DataSourceName.MASTER;

    /**
     * 普通查询允许使用的只读副本名称。
     */
    private List<String> replicaDataSources = new ArrayList<>(List.of(DataSourceName.SLAVE));

    /**
     * 已创建、迁移并通过核验的季度节点后缀。
     */
    private List<String> physicalNodes = new ArrayList<>();

    /**
     * 风控审计分片必须完整包含的逻辑表集合。
     */
    private List<String> logicTables = new ArrayList<>(defaultLogicTables());

    /**
     * 允许注册 riskAudit 数据源的服务白名单。
     */
    private List<String> directAccessServices = new ArrayList<>(List.of("service-data", "service-admin"));

    /**
     * 返回风控审计分片的完整逻辑表基线。
     *
     * @return 按主记录、执行明细排序的不可变表清单
     */
    public static List<String> defaultLogicTables() {
        return List.of(EVALUATION_RECORD_TABLE, EVALUATION_DETAIL_TABLE);
    }

    /**
     * 在启用 ShardingSphere 路由前校验版本、表清单、节点和 checksum，拒绝半份配置启动。
     */
    public void validateForActivation() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(ruleVersion) || "unpublished".equalsIgnoreCase(ruleVersion.trim())) {
            throw new IllegalStateException("risk audit sharding rule version must be published before activation");
        }
        if (!StringUtils.hasText(ruleChecksum) || !ruleChecksum.matches("sha256:[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("risk audit sharding rule checksum must be sha256 hex");
        }
        if (!TransactionShardingProperties.REQUIRED_ZONE_ID.equals(databaseZoneId)) {
            throw new IllegalStateException("risk audit sharding database zone must be Asia/Shanghai");
        }
        if (!REQUIRED_SHARDING_COLUMN.equals(shardingColumn)) {
            throw new IllegalStateException("risk audit sharding column must be evaluation_time");
        }
        if (!StringUtils.hasText(primaryDataSource)) {
            throw new IllegalStateException("risk audit primary data source is required");
        }
        if (physicalNodes == null || physicalNodes.isEmpty()
                || physicalNodes.stream().anyMatch(node -> node == null || !node.matches("\\d{4}0[1-4]"))
                || physicalNodes.stream().distinct().count() != physicalNodes.size()) {
            throw new IllegalStateException("risk audit physical nodes must be unique yyyyQQ suffixes");
        }
        if (logicTables == null
                || logicTables.stream().distinct().count() != defaultLogicTables().size()
                || logicTables.size() != defaultLogicTables().size()
                || !logicTables.containsAll(defaultLogicTables())) {
            throw new IllegalStateException("risk audit sharding rules must contain both formal logic tables");
        }
        String calculated = RiskAuditShardingRuleChecksum.calculate(this);
        if (!calculated.equalsIgnoreCase(ruleChecksum)) {
            throw new IllegalStateException("risk audit sharding rule checksum mismatch for version " + ruleVersion);
        }
    }
}
