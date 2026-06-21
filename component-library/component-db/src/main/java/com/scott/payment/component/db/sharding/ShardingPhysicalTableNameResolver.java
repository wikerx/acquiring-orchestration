package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 分表物理表名解析器。
 *
 * <p>所有进入 DDL 的表名必须先通过该类校验，只允许字母、数字和下划线，
 * 禁止把未校验的用户输入拼入 SQL。</p>
 */
@Component
public class ShardingPhysicalTableNameResolver {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,127}$");

    /**
     * 根据单表规则和季度生成物理表名。
     *
     * @param rule    单表分表规则
     * @param quarter 目标季度
     * @return 安全物理表名
     */
    public String physicalTableName(PaymentQuarterShardingProperties.TableRule rule, ShardingQuarter quarter) {
        if (rule == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding table rule is required");
        }
        if (quarter == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding quarter is required");
        }
        String logicalTable = requireSafeIdentifier(rule.getLogicalTable(), "logical table");
        String format = rule.getTableNameFormat();
        if (format == null || format.isBlank()) {
            format = "%s_%d%02d";
        }
        String physicalTable = String.format(format, logicalTable, quarter.year(), quarter.quarter());
        return requireSafeIdentifier(physicalTable, "physical table");
    }

    /**
     * 读取并校验模板表名。
     *
     * @param rule 单表分表规则
     * @return 安全模板表名
     */
    public String templateTableName(PaymentQuarterShardingProperties.TableRule rule) {
        if (rule == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding table rule is required");
        }
        String templateTable = rule.getTemplateTable();
        if (templateTable == null || templateTable.isBlank()) {
            templateTable = rule.getLogicalTable();
        }
        return requireSafeIdentifier(templateTable, "template table");
    }

    /**
     * 读取并校验自增主键字段名。
     *
     * @param rule 单表分表规则
     * @return 安全字段名
     */
    public String idColumnName(PaymentQuarterShardingProperties.TableRule rule) {
        String idColumn = rule == null ? null : rule.getIdColumn();
        if (idColumn == null || idColumn.isBlank()) {
            idColumn = "id";
        }
        return requireSafeIdentifier(idColumn, "id column");
    }

    /**
     * 校验 SQL 标识符。
     *
     * @param value 标识符值
     * @param label 错误提示标签
     * @return 原始标识符
     */
    public String requireSafeIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), label + " is required");
        }
        String trimmed = value.trim();
        if (!SAFE_IDENTIFIER.matcher(trimmed).matches()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), label + " contains unsafe characters");
        }
        return trimmed;
    }
}
