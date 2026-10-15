package com.scott.payment.component.db.reference.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinRangeIndexEntry
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : Redis Card BIN 最长公共前缀字段中的数据库区间快照，按覆盖关系和最大主键还原 SQL 语义
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
                                     String issuerBank) {

    /**
     * 判断启用记录是否覆盖十一位查询值。
     *
     * <p>生效与失效由管理端落库时转换为 {@code status}，查询侧只消费
     * {@code status=1} 的发布快照，保持 Redis 与单条回源 SQL 的条件一致。</p>
     *
     * @param numericValue 规范化后的十一位查询数值
     * @return true 表示当前区间覆盖查询值
     */
    public boolean matches(long numericValue) {
        return cardBinStart <= numericValue
                && cardBinEnd >= numericValue;
    }
}
