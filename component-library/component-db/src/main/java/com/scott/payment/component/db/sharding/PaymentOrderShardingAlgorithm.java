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
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : PaymentOrderShardingAlgorithm Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
     * @param logicalTableName    逻辑表名，例如 test_transaction、test_transaction_info
     * @param transactionDateTime 交易时间，数据库统一按 UTC+8 保存和路由
     * @return 物理表名，例如 test_transaction_202602
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
        if (properties == null || properties.getTables() == null || logicalTableName == null) {
            return false;
        }
        return properties.getTables().containsKey(logicalTableName)
                || properties.getTables().values().stream()
                .anyMatch(rule -> logicalTableName.equals(rule.getLogicalTable()));
    }

    /**
     * 完成 quarter 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private int quarter(LocalDateTime transactionDateTime) {
        return (transactionDateTime.getMonthValue() - 1) / 3 + 1;
    }

    /**
     * 构建 build Default Properties 对应的领域对象、请求对象或日志对象。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 转换或构建后的目标对象
     */
    private static PaymentQuarterShardingProperties buildDefaultProperties() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        addDefaultRule(properties, "test_transaction", "测试交易主表");
        addDefaultRule(properties, "test_transaction_info", "测试交易信息表");
        addDefaultRule(properties, "test_transaction_merge_info", "测试交易附属信息表");
        addDefaultRule(properties, "test_transaction_status_info", "测试交易状态信息表");
        return properties;
    }

    /**
     * 计算 add Default Rule 对应的数值结果，调用方负责保证金额和币种上下文一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param properties properties 输入值，含义由调用方法名称和所属业务对象限定
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param description description 输入值，含义由调用方法名称和所属业务对象限定
     */
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

    /**
     * 校验 validate Required 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logicalTableName logical Table Name 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void validateRequired(String logicalTableName, LocalDateTime transactionDateTime) {
        if (logicalTableName == null || logicalTableName.trim().isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "logicalTableName is required");
        }
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
    }

/**
 * 完成 get Table Rule 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param properties properties 输入值，含义由调用方法名称和所属业务对象限定
 * @param logicalTableName logical Table Name 输入值，含义由调用方法名称和所属业务对象限定
 * @return 当前方法计算或转换后的业务结果
 */
    private PaymentQuarterShardingProperties.TableRule getTableRule(PaymentQuarterShardingProperties properties,
                                                                    String logicalTableName) {
        if (properties == null || properties.getTables() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding tables config is required");
        }
        Map<String, PaymentQuarterShardingProperties.TableRule> tables = properties.getTables();
        PaymentQuarterShardingProperties.TableRule tableRule = tables.get(logicalTableName);
        if (tableRule == null) {
            tableRule = tables.values().stream()
                    .filter(rule -> logicalTableName.equals(rule.getLogicalTable()))
                    .findFirst()
                    .orElse(null);
        }
        if (tableRule == null || Boolean.FALSE.equals(tableRule.getEnabled())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "logicalTableName is not configured for sharding");
        }
        if (tableRule.getLogicalTable() == null || tableRule.getLogicalTable().trim().isEmpty()) {
            tableRule.setLogicalTable(logicalTableName);
        }
        if (tableRule.getTableNameFormat() == null || tableRule.getTableNameFormat().trim().isEmpty()) {
            tableRule.setTableNameFormat("%s_%d%02d");
        }
        return tableRule;
    }

    /**
     * 校验 validate Quarter 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param year year 输入值，含义由调用方法名称和所属业务对象限定
     * @param quarter quarter 输入值，含义由调用方法名称和所属业务对象限定
     * @param label label 输入值，含义由调用方法名称和所属业务对象限定
     */
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

    /**
     * 完成 quarter Index 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param year year 输入值，含义由调用方法名称和所属业务对象限定
     * @param quarter quarter 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private int quarterIndex(int year, int quarter) {
        return year * 4 + quarter - 1;
    }
}
