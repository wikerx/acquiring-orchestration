package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateRuleExportRow
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : 汇率汇率规则导出行模型，位于 运营后台服务，定义 Excel 列及运营可见值，不承载数据库写入规则。
 * @status : create
 */
@Data
public class ExchangeRateRuleExportRow {

    /**
     * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;

    /**
     * 来源编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.sourceCode", width = 18)
    private String sourceCode;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;

    /**
     * 汇率字段字段，保存 {@code ExchangeRateRuleExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.rule.rateField", width = 18)
    private String rateField;

    /**
     * {@code adjustDirection}字段，保存 {@code ExchangeRateRuleExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.rule.adjustDirection", width = 16)
    private String adjustDirection;

    /**
     * {@code adjustMethod}，表示支付方式、通知方式或调用方式。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.rule.adjustMethod", width = 16)
    private String adjustMethod;

    /**
     * {@code adjustValue}字段，保存 {@code ExchangeRateRuleExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.rule.adjustValue", width = 18)
    private BigDecimal adjustValue;

    /**
     * {@code decimalScale}字段，保存 {@code ExchangeRateRuleExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.rule.decimalScale", width = 14)
    private Integer decimalScale;

    /**
     * 舍入模式字段，保存 {@code ExchangeRateRuleExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.rule.roundingMode", width = 18)
    private String roundingMode;

    /**
     * 匹配优先级，数值越小越优先。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.source.priority", width = 12)
    private Integer priority;

    /**
     * 生效开始时间字段，保存 {@code ExchangeRateRuleExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.rule.effectiveStartTime", width = 22)
    private LocalDateTime effectiveStartTime;

    /**
     * 生效结束时间字段，保存 {@code ExchangeRateRuleExportRow} 当前处理所需的业务取值。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.rule.effectiveEndTime", width = 22)
    private LocalDateTime effectiveEndTime;

    /**
     * 规则状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.rule.ruleStatus", width = 12)
    private String ruleStatus;

    /**
     * 收单支付备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;

    /**
     * 记录最后更新时间，持久化精度为毫秒。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
     * </p>
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.exchange.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
