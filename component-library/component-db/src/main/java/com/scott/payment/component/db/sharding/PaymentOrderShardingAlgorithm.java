package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentOrderShardingAlgorithm
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 支付订单季度分表算法
 * @status : create
 */
public class PaymentOrderShardingAlgorithm {

    /**
     * 数据库统一时区，交易分表字段 transaction_date_time 按 UTC+8 计算季度。
     */
    private static final ZoneId DATABASE_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 默认分表配置，用于单元测试、早期骨架代码以及未接入 Nacos 配置的调用场景。
     */
    private static final PaymentQuarterShardingProperties DEFAULT_PROPERTIES = buildDefaultProperties();

    /**
     * 根据默认配置和交易时间计算季度分表表名。
     * <p>
     * 所有需要分表的业务表必须传入 transaction_date_time，禁止使用订单号或商户号猜测路由。
     *
     * @param logicalTableName    逻辑表名，例如 transaction、payment_order、payout_order
     * @param transactionDateTime 交易时间，数据库统一按 UTC+8 保存和路由
     * @return 物理表名，例如 transaction_2026_q2
     */
    public String tableName(String logicalTableName, LocalDateTime transactionDateTime) {
        return tableName(DEFAULT_PROPERTIES, logicalTableName, transactionDateTime);
    }

    /**
     * 根据默认配置和时间戳类型交易时间计算季度分表表名。
     * <p>
     * 该重载用于兼容仍以时间戳对象传参的旧代码，内部会立即转成 {@link LocalDateTime} 继续路由。
     *
     * @param logicalTableName    逻辑表名
     * @param transactionDateTime 交易时间
     * @return 物理表名
     */
    public String tableName(String logicalTableName, Instant transactionDateTime) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        return tableName(logicalTableName, LocalDateTime.ofInstant(transactionDateTime, DATABASE_ZONE_ID));
    }

    /**
     * 根据指定 Nacos 分表配置和交易时间计算季度分表表名。
     *
     * @param properties          Nacos sharding-{env}.yaml 映射后的分表配置
     * @param logicalTableName    逻辑表名
     * @param transactionDateTime 交易时间，必须是数据库统一时区语义下的时间
     * @return 物理表名
     */
    public String tableName(PaymentQuarterShardingProperties properties,
                            String logicalTableName,
                            LocalDateTime transactionDateTime) {
        validateRequired(logicalTableName, transactionDateTime);
        PaymentQuarterShardingProperties.TableRule tableRule = getTableRule(properties, logicalTableName);
        validateQuarter(tableRule.getStartYear(), tableRule.getStartQuarter(), "start");
        validateQuarter(tableRule.getEndYear(), tableRule.getEndQuarter(), "end");

        int year = transactionDateTime.getYear();
        int quarter = quarter(transactionDateTime);
        int routeQuarterIndex = quarterIndex(year, quarter);
        int startQuarterIndex = quarterIndex(tableRule.getStartYear(), tableRule.getStartQuarter());
        int endQuarterIndex = quarterIndex(tableRule.getEndYear(), tableRule.getEndQuarter());
        if (routeQuarterIndex < startQuarterIndex || routeQuarterIndex > endQuarterIndex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "transaction_date_time is outside sharding table range");
        }
        return String.format(tableRule.getTableNameFormat(), tableRule.getLogicalTable(), year, quarter);
    }

    /**
     * 根据指定 Nacos 分表配置和时间戳类型交易时间计算季度分表表名。
     * <p>
     * 该重载只负责兼容旧入参类型，真正的季度路由仍统一走 {@link LocalDateTime} 语义。
     *
     * @param properties          Nacos sharding-{env}.yaml 映射后的分表配置
     * @param logicalTableName    逻辑表名
     * @param transactionDateTime 交易时间
     * @return 物理表名
     */
    public String tableName(PaymentQuarterShardingProperties properties,
                            String logicalTableName,
                            Instant transactionDateTime) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        ZoneId zoneId = ZoneId.of(properties.getDatabaseTimezone());
        return tableName(properties, logicalTableName, LocalDateTime.ofInstant(transactionDateTime, zoneId));
    }

    /**
     * 生成当前逻辑表在配置范围内的全部物理表名。
     * <p>
     * 建表脚本、巡检脚本和发布前检查都可以使用该方法确认哪些表应该存在。
     *
     * @param properties       Nacos sharding-{env}.yaml 映射后的分表配置
     * @param logicalTableName 逻辑表名
     * @return 物理表名列表，按时间从早到晚排序
     */
    public List<String> physicalTables(PaymentQuarterShardingProperties properties, String logicalTableName) {
        PaymentQuarterShardingProperties.TableRule tableRule = getTableRule(properties, logicalTableName);
        List<String> tableNames = new ArrayList<>();
        int currentQuarterIndex = quarterIndex(tableRule.getStartYear(), tableRule.getStartQuarter());
        int endQuarterIndex = quarterIndex(tableRule.getEndYear(), tableRule.getEndQuarter());
        while (currentQuarterIndex <= endQuarterIndex) {
            int year = currentQuarterIndex / 4;
            int quarter = currentQuarterIndex % 4 + 1;
            tableNames.add(String.format(tableRule.getTableNameFormat(), tableRule.getLogicalTable(), year, quarter));
            currentQuarterIndex++;
        }
        return tableNames;
    }

    /**
     * 判断逻辑表是否已经被纳入分表配置。
     *
     * @param properties       Nacos sharding-{env}.yaml 映射后的分表配置
     * @param logicalTableName 逻辑表名
     * @return true 表示该表参与季度分表
     */
    public boolean containsShardingTable(PaymentQuarterShardingProperties properties, String logicalTableName) {
        return properties != null
                && properties.getTables() != null
                && properties.getTables().containsKey(logicalTableName);
    }

    private int quarter(LocalDateTime transactionDateTime) {
        return (transactionDateTime.getMonthValue() - 1) / 3 + 1;
    }

    private static PaymentQuarterShardingProperties buildDefaultProperties() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        addDefaultRule(properties, "transaction", "交易流水主表");
        addDefaultRule(properties, "payment_order", "收单支付订单表");
        addDefaultRule(properties, "payout_order", "代付订单表");
        return properties;
    }

    private static void addDefaultRule(PaymentQuarterShardingProperties properties, String logicalTable, String description) {
        PaymentQuarterShardingProperties.TableRule tableRule = new PaymentQuarterShardingProperties.TableRule();
        tableRule.setLogicalTable(logicalTable);
        tableRule.setStartYear(2026);
        tableRule.setStartQuarter(1);
        tableRule.setEndYear(2035);
        tableRule.setEndQuarter(4);
        tableRule.setDescription(description);
        properties.getTables().put(logicalTable, tableRule);
    }

    private void validateRequired(String logicalTableName, LocalDateTime transactionDateTime) {
        if (logicalTableName == null || logicalTableName.trim().isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "logicalTableName is required");
        }
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
    }

    private PaymentQuarterShardingProperties.TableRule getTableRule(PaymentQuarterShardingProperties properties,
                                                                    String logicalTableName) {
        if (properties == null || properties.getTables() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding tables config is required");
        }
        Map<String, PaymentQuarterShardingProperties.TableRule> tables = properties.getTables();
        PaymentQuarterShardingProperties.TableRule tableRule = tables.get(logicalTableName);
        if (tableRule == null || Boolean.FALSE.equals(tableRule.getEnabled())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "logicalTableName is not configured for sharding");
        }
        if (tableRule.getLogicalTable() == null || tableRule.getLogicalTable().trim().isEmpty()) {
            tableRule.setLogicalTable(logicalTableName);
        }
        if (tableRule.getTableNameFormat() == null || tableRule.getTableNameFormat().trim().isEmpty()) {
            tableRule.setTableNameFormat("%s_%d_q%d");
        }
        return tableRule;
    }

    private void validateQuarter(Integer year, Integer quarter, String label) {
        if (year == null || quarter == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                    label + " sharding year and quarter are required");
        }
        if (quarter < 1 || quarter > 4) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    label + " sharding quarter must be between 1 and 4");
        }
    }

    private int quarterIndex(int year, int quarter) {
        return year * 4 + quarter - 1;
    }
}
