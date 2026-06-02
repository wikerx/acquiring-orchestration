package com.scott.payment.component.core.iso;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyInfo
 * @date : 2026-06-02 15:42
 * @email : scott_x@163.com
 * @description : ISO 4217 币种信息
 * @status : create
 *
 * @param alphabeticCode       ISO 4217 三位字母币种代码，例如 USD、CNY
 * @param numericCode          ISO 4217 三位数字币种代码，例如 840、156
 * @param englishName          英文币种名称，例如 US Dollar
 * @param chineseName          中文币种名称，例如 美元
 * @param defaultFractionDigits 默认辅币位，小于 0 表示 JDK 无可用辅币位定义
 * @param minorUnitMultiplier  最小单位换算倍数，例如 USD 为 100，JPY 为 1
 */
public record IsoCurrencyInfo(String alphabeticCode,
                              String numericCode,
                              String englishName,
                              String chineseName,
                              int defaultFractionDigits,
                              long minorUnitMultiplier) {

    /**
     * ISO 4217 没有两位字母币种代码，该字段固定返回 false，用于提醒调用方不要把国家代码当成币种代码。
     *
     * @return 是否存在 ISO 标准两位字母币种代码
     */
    public boolean hasStandardAlpha2Code() {
        return false;
    }
}
