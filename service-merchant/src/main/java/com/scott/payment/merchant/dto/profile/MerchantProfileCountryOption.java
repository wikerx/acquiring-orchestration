package com.scott.payment.merchant.dto.profile;

/**
 * 商户资料表单允许选择的国家或地区展示信息。
 *
 * @param alpha3Code 资料请求使用的三字母代码
 * @param alpha2Code 用于展示回退的两字母代码
 * @param chineseName 中文名称
 * @param englishName 英文名称
 * @param shortEnglishName 英文简称
 * @param flagEmoji 国家或地区旗帜
 */
public record MerchantProfileCountryOption(
        String alpha3Code,
        String alpha2Code,
        String chineseName,
        String englishName,
        String shortEnglishName,
        String flagEmoji) {
}
