package com.scott.payment.component.db.sharding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentQuarterShardingProperties
 * @date : 2026-05-29 00:00
 * @email : scott_x@163.com
 * @description : 支付系统季度分表配置模型，对应 Nacos sharding-{env}.yaml 中 global-payment.sharding 节点
 * @status : create
 */
@Data
@Component
@ConfigurationProperties(prefix = "global-payment.sharding")
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
     * 分表规则集合，key 建议使用逻辑表名，value 描述该逻辑表的起止分表范围和命名格式。
     */
    private Map<String, TableRule> tables = new LinkedHashMap<>();

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : TableRule
     * @date : 2026-05-29 00:00
     * @email : scott_x@163.com
     * @description : 单张逻辑表的季度分表规则
     * @status : create
     */
    @Data
    public static class TableRule {

        /**
         * 是否启用当前逻辑表的分表路由。新增表可以先配置为 false，建表和数据迁移完成后再打开。
         */
        private Boolean enabled = Boolean.TRUE;

        /**
         * 逻辑表名，例如 transaction、payment_order、payout_order。
         */
        private String logicalTable;

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
         * 物理表名格式，默认格式为逻辑表名_年份_q季度，例如 transaction_2026_q2。
         */
        private String tableNameFormat = "%s_%d_q%d";

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
