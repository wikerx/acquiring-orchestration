package com.scott.payment.component.db.sharding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "global-payment.sharding")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentQuarterShardingProperties
 * @date : 2026-05-29 18:36
 * @email : scott_x@163.com
 * @description : Payment Quarter Sharding Properties 配置属性模型，位于 公共组件库，绑定 application 配置项并提供运行时默认值。
 * @status : create
 */
public class PaymentQuarterShardingProperties {

    /**
     * 分表策略标识，当前固定为 quarter，表示按交易时间所在季度路由。
     */
    private String strategy = "quarter";

    /**
     * 默认分表字段，所有参与分表的业务表必须传入该字段，避免按订单号或商户号猜测表路由。
     */
    private String shardingColumn = "transaction_date_time";

    /**
     * 数据库统一时区，交易时间按该时区换算季度，当前支付系统统一使用 UTC+8。
     */
    private String databaseTimezone = "Asia/Shanghai";

    /**
     * 分表物理表维护策略。
     */
    private TableMaintenance tableMaintenance = new TableMaintenance();

    /**
     * 分表物理表主键自增规则。
     */
    private IdGenerator idGenerator = new IdGenerator();

    /**
     * 分表规则集合，key 建议使用逻辑表名，value 描述该逻辑表的起止分表范围和命名格式。
     */
    private Map<String, TableRule> tables = new LinkedHashMap<>();

    /**
     * 分表物理表维护策略。
     *
     * <p>DDL 相关开关必须显式配置，避免在未确认的环境中自动建表或修改结构。</p>
     */
    @Data
    public static class TableMaintenance {

        /**
         * 是否启用分表物理表治理能力。
         */
        private Boolean enabled = Boolean.TRUE;

        /**
         * 是否处理当前季度物理表。
         */
        private Boolean preCreateCurrentQuarter = Boolean.TRUE;

        /**
         * 是否处理下一季度物理表。
         */
        private Boolean preCreateNextQuarter = Boolean.TRUE;

        /**
         * 目标表已存在时是否对比模板表结构。
         */
        private Boolean compareSchemaIfExists = Boolean.TRUE;

        /**
         * DDL 使用的数据源名称，必须保持为主库。
         */
        private String ddlDataSource = "master";

        /**
         * 新建物理表后是否设置 AUTO_INCREMENT 起始值。
         */
        private Boolean setAutoIncrementStartValue = Boolean.TRUE;

        /**
         * 自增序号宽度，yyyyQQ 后拼接该宽度的序号。
         */
        private Integer autoIncrementSequenceWidth = 12;

        /**
         * 是否允许从模板表复制结构创建物理表。
         */
        private Boolean allowCreateFromTemplateTable = Boolean.TRUE;

        /**
         * 是否允许自动修改已存在表结构。第一版必须保持 false。
         */
        private Boolean allowAlterExistingTable = Boolean.FALSE;
    }

    /**
     * 分表物理表主键自增规则。
     */
    @Data
    public static class IdGenerator {

        /**
         * ID 生成模式，当前支持 mysql-auto-increment-prefix。
         */
        private String mode = "mysql-auto-increment-prefix";

        /**
         * ID 前缀格式，当前固定为 yyyyQQ。
         */
        private String prefixFormat = "yyyyQQ";

        /**
         * 自增序号宽度。
         */
        private Integer sequenceWidth = 12;

        /**
         * 每个季度物理表的起始自增序号。
         */
        private Long startSequence = 1L;

        /**
         * 每个季度物理表的最大安全自增序号。
         */
        private Long maxSequence = 999_999_999_999L;
    }

    /**
     * 单张逻辑表的季度分表规则。
     */
    @Data
    public static class TableRule {

        /**
         * 是否启用当前逻辑表的分表路由。新增表可以先配置为 false，建表和数据迁移完成后再打开。
         */
        private Boolean enabled = Boolean.TRUE;

        /**
         * 逻辑表名，例如 test_transaction、test_transaction_info。
         */
        private String logicalTable;

        /**
         * 建表模板表名，自动预建物理表时通过 CREATE TABLE target LIKE template 复制结构。
         */
        private String templateTable;

        /**
         * 自增主键字段名，当前测试分表统一使用 id。
         */
        private String idColumn = "id";

        /**
         * 当前逻辑表使用的分表字段，默认与全局字段 transaction_date_time 保持一致。
         */
        private String shardingColumn = "transaction_date_time";

        /**
         * 起始分表年份。不同环境可从不同年份开始，避免 dev/test/prod 的历史表范围强绑定。
         */
        private Integer startYear;

        /**
         * 起始分表季度，取值范围 1 到 4。
         */
        private Integer startQuarter;

        /**
         * 结束分表年份。后续追加分表时优先扩展该字段。
         */
        private Integer endYear;

        /**
         * 结束分表季度，取值范围 1 到 4。
         */
        private Integer endQuarter;

        /**
         * 物理表名格式，默认格式为逻辑表名_yyyyQQ，例如 test_transaction_202602。
         */
        private String tableNameFormat = "%s_%d%02d";

        /**
         * 物理表所在数据源，当前分表表默认放在 master 写库。
         */
        private String actualDataSource = "master";

        /**
         * 规则说明，用于在 Nacos 中标注当前表为何参与分表。
         */
        private String description;
    }
}
