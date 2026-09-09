package com.scott.payment.component.db.sharding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingGovernanceProperties
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 交易季度物理表治理配置，只供 Job/Admin 的模板、DDL、schema 和号段治理链路使用，不参与业务 SQL 路由。
 * @status : create
 */
@Component
@ConfigurationProperties(prefix = "transaction-sharding.governance")
@Data
public class TransactionShardingGovernanceProperties {

    /** 治理侧固定使用季度策略。 */
    private String strategy = "quarter";
    /** 治理校验使用的交易分片列。 */
    private String shardingColumn = TransactionShardingProperties.REQUIRED_SHARDING_COLUMN;
    /** 治理计算季度时使用的数据库时区。 */
    private String databaseTimezone = TransactionShardingProperties.REQUIRED_ZONE_ID;
    /** 物理表创建与结构检查策略。 */
    private TableMaintenance tableMaintenance = new TableMaintenance();
    /** MySQL 自增号段规则。 */
    private IdGenerator idGenerator = new IdGenerator();
    /** 28 张正式交易逻辑表的物理治理规则。 */
    private Map<String, TableRule> tables = new LinkedHashMap<>();

    /** 在规则结束前多少个季度发出续期告警。 */
    private int expiryWarningQuarters = 4;

    /** Admin 治理页面从当前季度起最多展示的规划季度数，避免长期协议上限被完整展开。 */
    private int planningHorizonQuarters = 8;

    /**
     * 返回规则到期前的预警窗口。
     *
     * @return 预警季度数
     */
    public int getExpiryWarningQuarters() {
        return expiryWarningQuarters;
    }

    /**
     * 设置规则到期前的预警窗口；实际治理任务会把非正数收敛为一个季度。
     *
     * @param expiryWarningQuarters 预警季度数
     */
    public void setExpiryWarningQuarters(int expiryWarningQuarters) {
        this.expiryWarningQuarters = expiryWarningQuarters;
    }

    /** 物理表维护开关；任何真实 DDL 仍需外部审批和任务显式非 dry-run。 */
    @Data
    public static class TableMaintenance {
        /** 是否启用物理表治理能力。 */
        private Boolean enabled = Boolean.TRUE;
        /** 是否处理当前季度。 */
        private Boolean preCreateCurrentQuarter = Boolean.TRUE;
        /** 是否处理下一季度。 */
        private Boolean preCreateNextQuarter = Boolean.TRUE;
        /** 已存在物理表是否与模板进行结构比对。 */
        private Boolean compareSchemaIfExists = Boolean.TRUE;
        /** DDL 使用的治理直连数据源，必须为主库。 */
        private String ddlDataSource = "master";
        /** 建表后是否设置并验证自增起始值。 */
        private Boolean setAutoIncrementStartValue = Boolean.TRUE;
        /** yyyyQQ 后的自增序号宽度。 */
        private Integer autoIncrementSequenceWidth = 12;
        /** 是否允许通过模板复制缺失物理表。 */
        private Boolean allowCreateFromTemplateTable = Boolean.TRUE;
        /** 第一版禁止在线自动修改已存在表结构。 */
        private Boolean allowAlterExistingTable = Boolean.FALSE;
    }

    /** 季度物理表的 MySQL 自增主键号段规则。 */
    @Data
    public static class IdGenerator {
        /** ID 生成模式。 */
        private String mode = "mysql-auto-increment-prefix";
        /** 季度前缀格式。 */
        private String prefixFormat = "yyyyQQ";
        /** 季度前缀后的序号宽度。 */
        private Integer sequenceWidth = 12;
        /** 每季度起始序号。 */
        private Long startSequence = 1L;
        /** 每季度允许的最大安全序号。 */
        private Long maxSequence = 999_999_999_999L;
    }

    /** 单张正式交易逻辑表的物理表治理规则。 */
    @Data
    public static class TableRule {
        /** 是否纳入预建与检查。 */
        private Boolean enabled = Boolean.TRUE;
        /** ShardingSphere 使用的逻辑表名。 */
        private String logicalTable;
        /** CREATE TABLE LIKE 使用的模板表。 */
        private String templateTable;
        /** 自增主键字段名。 */
        private String idColumn = "id";
        /** 固定交易分片列。 */
        private String shardingColumn = TransactionShardingProperties.REQUIRED_SHARDING_COLUMN;
        /** 治理覆盖的起始年份。 */
        private Integer startYear;
        /** 治理覆盖的起始季度，1 到 4。 */
        private Integer startQuarter;
        /** 治理覆盖的结束年份。 */
        private Integer endYear;
        /** 治理覆盖的结束季度，1 到 4。 */
        private Integer endQuarter;
        /** 物理表名格式，默认逻辑表_yyyyQQ。 */
        private String tableNameFormat = "%s_%d%02d";
        /** 治理 DDL 和 schema 检查使用的主库数据源名。 */
        private String actualDataSource = "master";
        /** 规则业务说明。 */
        private String description;
    }
}
