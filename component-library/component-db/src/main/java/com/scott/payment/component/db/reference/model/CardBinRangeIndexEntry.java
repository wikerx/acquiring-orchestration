package com.scott.payment.component.db.reference.model;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinRangeIndexEntry
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : Redis Card BIN 六位桶中的数据库区间快照，保留时间边界和 SQL 最优匹配所需排序字段
 * @status : create
 */
public record CardBinRangeIndexEntry(Long id,
                                     long cardBinStart,
                                     long cardBinEnd,
                                     int binLength,
                                     String cardBrand,
                                     String cardSubBrand,
                                     String cardType,
                                     String cardLevel,
                                     String issuerCountryName,
                                     String issuerCountryAlpha2,
                                     String issuerCountryAlpha3,
                                     String issuerCountryNumeric,
                                     String issuerBank,
                                     LocalDateTime effectiveTime,
                                     LocalDateTime expireTime,
                                     LocalDateTime updateTime) {

    /**
     * 判断记录在指定时刻是否覆盖十一位查询值。
     */
    public boolean matches(long numericValue, LocalDateTime now) {
        return cardBinStart <= numericValue
                && cardBinEnd >= numericValue
                && (effectiveTime == null || !effectiveTime.isAfter(now))
                && (expireTime == null || expireTime.isAfter(now));
    }
}
