package com.scott.payment.payment.model;

import lombok.Data;

import java.io.Serializable;

/** 按卡号前 11 位缓存的 BIN 查询结果；matched=false 用于避免数据库未命中时重复查询。 */
@Data
public class PaymentCardBinCacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cardBinPrefix;
    private Boolean matched;
    private Long rangeId;
    private Integer binLength;
    private String cardBrand;
    private String issuerCountryAlpha2;
    private String issuerCountryAlpha3;
    private String issuerCountryName;

    public static PaymentCardBinCacheEntry miss(String cardBinPrefix) {
        PaymentCardBinCacheEntry entry = new PaymentCardBinCacheEntry();
        entry.setCardBinPrefix(cardBinPrefix);
        entry.setMatched(Boolean.FALSE);
        return entry;
    }
}
