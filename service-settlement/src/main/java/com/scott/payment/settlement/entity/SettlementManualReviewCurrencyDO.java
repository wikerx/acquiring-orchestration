package com.scott.payment.settlement.entity;

import lombok.Data;

/**
 * 手动预审范围内需要冻结汇率的来源币种及 exponent。
 */
@Data
public class SettlementManualReviewCurrencyDO {
    /**
     * 币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String currency;
    /**
     * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
     * <p>
     * 单位：位；格式：非负整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：必须等于 ISO 4217 币种精度，禁止默认按 2 位处理；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private Integer currencyExponent;
}
