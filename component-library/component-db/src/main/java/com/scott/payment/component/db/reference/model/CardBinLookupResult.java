package com.scott.payment.component.db.reference.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinLookupResult
 * @date : 2026-08-11 15:38
 * @email : scott_x@163.com
 * @description : 公共卡 BIN 检索结果，仅返回归属信息，不暴露区间、来源批次或审计字段
 * @status : create
 *
 * @param matched                是否命中有效卡 BIN 区间，不允许为空
 * @param cardBin                商户提交的 6 至 11 位 BIN，可识别字段，日志中不得完整输出
 * @param binLength              命中记录精度，允许为空
 * @param cardBrand              卡品牌代码，允许为空
 * @param cardSubBrand           卡子品牌或产品名称，允许为空
 * @param cardType               卡类型代码，允许为空
 * @param cardLevel              卡等级，允许为空
 * @param issuerCountryName      发卡国家或地区名称，允许为空
 * @param issuerCountryAlpha2    发卡国家或地区 ISO Alpha-2 编码，允许为空
 * @param issuerCountryAlpha3    发卡国家或地区 ISO Alpha-3 编码，允许为空
 * @param issuerCountryNumeric   发卡国家或地区 ISO Numeric 编码，允许为空
 * @param issuerBank             发卡行名称，允许为空
 */
public record CardBinLookupResult(Boolean matched,
                                  String cardBin,
                                  Integer binLength,
                                  String cardBrand,
                                  String cardSubBrand,
                                  String cardType,
                                  String cardLevel,
                                  String issuerCountryName,
                                  String issuerCountryAlpha2,
                                  String issuerCountryAlpha3,
                                  String issuerCountryNumeric,
                                  String issuerBank) {

    /**
     * 构造未命中的合法卡 BIN 查询结果。
     *
     * @param cardBin 商户提交的卡 BIN
     * @return 未命中结果
     */
    public static CardBinLookupResult miss(String cardBin) {
        return new CardBinLookupResult(false, cardBin, null, null, null, null, null,
                null, null, null, null, null);
    }
}
