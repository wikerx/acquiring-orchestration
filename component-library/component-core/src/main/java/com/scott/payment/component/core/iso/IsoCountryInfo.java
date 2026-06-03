package com.scott.payment.component.core.iso;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryInfo
 * @date : 2026-06-02 15:40
 * @email : scott_x@163.com
 * @description : ISO 3166 国家地区信息
 * @status : create
 *
 * @param alpha2                 ISO 3166-1 alpha-2 两位国家代码，例如 US
 * @param alpha3                 ISO 3166-1 alpha-3 三位国家代码，例如 USA
 * @param numeric                ISO 3166-1 numeric 三位数字代码，例如 840
 * @param englishName            英文国家或地区全称，例如 United States of America
 * @param shortEnglishName       英文国家或地区简称，例如 United States
 * @param chineseName            中文国家或地区名称，例如 美国
 * @param continentCode          七大洲代码，例如 NA、AS、EU
 * @param continentName          七大洲中文名称，例如 北美洲、亚洲、欧洲
 * @param flagEmoji              国家或地区图标，例如 🇺🇸
 * @param primaryLanguageCode    主要语言代码；该字段不是 ISO 3166 强制字段，无法可靠判断时为空
 * @param primaryLanguageEnglish 主要语言英文名称
 * @param primaryLanguageChinese 主要语言中文名称
 * @param currencyAlpha3Code     默认币种 ISO 4217 三位字母代码；无默认币种时为空
 */
public record IsoCountryInfo(String alpha2,
                             String alpha3,
                             String numeric,
                             String englishName,
                             String shortEnglishName,
                             String chineseName,
                             String continentCode,
                             String continentName,
                             String flagEmoji,
                             String primaryLanguageCode,
                             String primaryLanguageEnglish,
                             String primaryLanguageChinese,
                             String currencyAlpha3Code) {
}
