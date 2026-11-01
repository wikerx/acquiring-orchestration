package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按来源币种聚合的手动交易或保证金结算候选统计，不跨币种直接相加。
 */
@Data
public class SettlementManualReviewPreviewLineDO {
    /**
     * 来源币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String sourceCurrency;
    /**
     * 来源币种小数位，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private Integer sourceCurrencyExponent;
    /**
     * 交易计数，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Long transactionCount;
    /**
     * {@code grossAmount}，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal grossAmount;
    /**
     * 平台费用金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal platformFeeAmount;
    /**
     * 保证金金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal reserveAmount;
    /**
     * {@code releasedReserveAmount}，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal releasedReserveAmount;
    /**
     * {@code netSettlementAmount}，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal netSettlementAmount;
    /**
     * 等待费用计数，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Long pendingFeeCount;
    /**
     * 持久化的{@code reserveDelayUnit}，用于还原当前记录的业务事实。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String reserveDelayUnit;
    /**
     * 持久化的{@code minimumReserveDelayDays}，用于还原当前记录的业务事实。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Integer minimumReserveDelayDays;
    /**
     * 持久化的{@code maximumReserveDelayDays}，用于还原当前记录的业务事实。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Integer maximumReserveDelayDays;
    /**
     * 持久化的{@code earliestExpectedReleaseDate}，用于还原当前记录的业务事实。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private java.time.LocalDate earliestExpectedReleaseDate;
    /**
     * 持久化的{@code latestExpectedReleaseDate}，用于还原当前记录的业务事实。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private java.time.LocalDate latestExpectedReleaseDate;
}
