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
 * @param alpha2        ISO 3166-1 alpha-2 两位国家代码，例如 US
 * @param alpha3        ISO 3166-1 alpha-3 三位国家代码，例如 USA
 * @param numeric       ISO 3166-1 numeric 三位数字代码，例如 840
 * @param englishName   英文国家名称，例如 United States
 * @param chineseName   中文国家名称，例如 美国
 * @param continentCode 洲代码，例如 NA、AS、EU
 * @param continentName 洲中文名称，例如 北美洲、亚洲、欧洲
 */
public record IsoCountryInfo(String alpha2,
                             String alpha3,
                             String numeric,
                             String englishName,
                             String chineseName,
                             String continentCode,
                             String continentName) {
}
