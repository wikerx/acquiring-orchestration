package com.scott.payment.component.core.iso;

/**
 * 币种界面展示信息，与金额精度和最小单位等货币计算语义隔离。
 *
 * @param alphabeticCode ISO 4217 三位字母代码
 * @param chineseName 中文名称
 * @param englishName 英文名称
 * @param currencySymbol 币种符号
 * @param iconKey 受控展示图标键，例如 flag:US、currency:XAU
 */
public record IsoCurrencyPresentationInfo(
        String alphabeticCode,
        String chineseName,
        String englishName,
        String currencySymbol,
        String iconKey) {
}
