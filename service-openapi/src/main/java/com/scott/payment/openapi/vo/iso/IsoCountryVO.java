package com.scott.payment.openapi.vo.iso;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryVO
 * @date : 2026-06-03 15:08
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 国家地区响应参数
 * @status : create
 */
@Data
public class IsoCountryVO implements Serializable {

    /**
     * 序列化版本号，用于保证响应对象在服务间传输或日志落库时兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * ISO 3166-1 alpha-2 两位字母代码。
     */
    private String alpha2;

    /**
     * ISO 3166-1 alpha-3 三位字母代码。
     */
    private String alpha3;

    /**
     * ISO 3166-1 numeric 三位数字代码。
     */
    private String numeric;

    /**
     * 国家或地区英文全称。
     */
    private String englishName;

    /**
     * 国家或地区英文简称。
     */
    private String shortEnglishName;

    /**
     * 国家或地区中文名称。
     */
    private String chineseName;

    /**
     * 七大洲代码。
     */
    private String continentCode;

    /**
     * 七大洲中文名称。
     */
    private String continentName;

    /**
     * 国家或地区图标。
     */
    private String flagEmoji;

    /**
     * 主要语言代码。
     */
    private String primaryLanguageCode;

    /**
     * 主要语言英文名称。
     */
    private String primaryLanguageEnglish;

    /**
     * 主要语言中文名称。
     */
    private String primaryLanguageChinese;

    /**
     * 默认币种 ISO 4217 三位字母代码。
     */
    private String currencyAlpha3Code;
}
