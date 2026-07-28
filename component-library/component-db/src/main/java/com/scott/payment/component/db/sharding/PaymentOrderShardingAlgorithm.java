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
 * @description : Payment Order Sharding Algorithm 协作组件，位于 公共组件库，封装 payment订单shardingalgorithm 相关的校验、转换、持久化访问或运行时协作入口。
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
     * 规范化quarter，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int quarter(LocalDateTime transactionDateTime) {
        return (transactionDateTime.getMonthValue() - 1) / 3 + 1;
    }

    /**
     * 构造defaultproperties对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @return 构造、转换或解析后的业务值
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
     * 创建默认规则，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 公共组件库 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param properties properties 输入值，参与 properties 的查询、校验、转换、写入或日志摘要
     * @param logicalTable 逻辑表名，用于按交易时间解析真实物理分表
     * @param description description 输入值，参与 description 的查询、校验、转换、写入或日志摘要
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
     * 校验required输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param logicalTableName logical Table Name 输入值，参与 logicaltablename 的查询、校验、转换、写入或日志摘要
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
 * 查询table规则，按调用方提供的过滤条件返回对应业务视图。
 * <p>
 * 前置条件：调用方已按 公共组件库 的权限和数据范围传入查询条件。
 * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
 * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
 * </p>
 * @param properties properties 输入值，参与 properties 的查询、校验、转换、写入或日志摘要
 * @param logicalTableName logical Table Name 输入值，参与 logicaltablename 的查询、校验、转换、写入或日志摘要
 * @return 查询得到的业务对象、分页结果或空结果
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
     * 校验quarter输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param year year 输入值，参与 year 的查询、校验、转换、写入或日志摘要
     * @param quarter quarter 输入值，参与 quarter 的查询、校验、转换、写入或日志摘要
     * @param label label 输入值，参与 label 的查询、校验、转换、写入或日志摘要
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
     * 规范化quarterindex，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param year year 输入值，参与 year 的查询、校验、转换、写入或日志摘要
     * @param quarter quarter 输入值，参与 quarter 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int quarterIndex(int year, int quarter) {
        return year * 4 + quarter - 1;
    }
}
