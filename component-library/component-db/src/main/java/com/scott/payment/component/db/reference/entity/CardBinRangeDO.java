package com.scott.payment.component.db.reference.entity;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinRangeDO
 * @date : 2026-08-11 15:38
 * @email : scott_x@163.com
 * @description : 卡 BIN 最优匹配只读投影，仅包含商户基础数据检索允许返回的归属字段
 * @status : create
 */
@Data
public class CardBinRangeDO {

    /** 命中 BIN 的精度长度，范围为 6 至 11，不允许为空。 */
    private Integer binLength;

    /** 卡品牌代码，允许为空，非敏感字段。 */
    private String cardBrand;

    /** 卡子品牌或产品名称，允许为空，非敏感字段。 */
    private String cardSubBrand;

    /** 卡类型代码，允许为空，非敏感字段。 */
    private String cardType;

    /** 卡等级，允许为空，非敏感字段。 */
    private String cardLevel;

    /** 发卡国家或地区名称，允许为空，非敏感字段。 */
    private String issuerCountryName;

    /** 发卡国家或地区 ISO Alpha-2 编码，允许为空，非敏感字段。 */
    private String issuerCountryAlpha2;

    /** 发卡国家或地区 ISO Alpha-3 编码，允许为空，非敏感字段。 */
    private String issuerCountryAlpha3;

    /** 发卡国家或地区 ISO Numeric 编码，允许为空，非敏感字段。 */
    private String issuerCountryNumeric;

    /** 发卡行名称，允许为空，非敏感字段。 */
    private String issuerBank;
}
