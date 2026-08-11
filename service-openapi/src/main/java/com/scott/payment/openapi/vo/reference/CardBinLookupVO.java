package com.scott.payment.openapi.vo.reference;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinLookupVO
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 卡 BIN 检索响应，不暴露内部区间、来源批次、状态或审计字段
 * @status : create
 */
@Data
public class CardBinLookupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否命中当前有效卡 BIN 区间，不允许为空。 */
    private Boolean matched;

    /** 商户提交的 6 至 11 位卡 BIN，不允许为空；响应 data 必须加密。 */
    private String cardBin;

    /** 命中记录的 BIN 精度，范围为 6 至 11，未命中时为空。 */
    private Integer binLength;

    /** 卡品牌代码，未命中时为空。 */
    private String cardBrand;

    /** 卡子品牌或产品名称，未命中时为空。 */
    private String cardSubBrand;

    /** 卡类型代码，未命中时为空。 */
    private String cardType;

    /** 卡等级，未命中时为空。 */
    private String cardLevel;

    /** 发卡国家或地区名称，未命中时为空。 */
    private String issuerCountryName;

    /** 发卡国家或地区 ISO Alpha-2 编码，未命中时为空。 */
    private String issuerCountryAlpha2;

    /** 发卡国家或地区 ISO Alpha-3 编码，未命中时为空。 */
    private String issuerCountryAlpha3;

    /** 发卡国家或地区 ISO Numeric 编码，未命中时为空。 */
    private String issuerCountryNumeric;

    /** 发卡行名称，未命中时为空。 */
    private String issuerBank;
}
