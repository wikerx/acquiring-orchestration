package com.scott.payment.component.db.reference.model;

/**
 * Card BIN Redis 快照读取状态，区分索引不可用与索引中的权威未命中
 *
 * @param available 活动快照是否可读
 * @param result 活动快照给出的命中或未命中结果；不可用时为空
 */
public record CardBinRangeIndexLookup(boolean available, CardBinLookupResult result) {

    /**
     * 构造索引不可用结果，用于区分基础设施故障与合法的 BIN 未命中。
     *
     * @return 不携带查询结果的不可用状态
     */
    public static CardBinRangeIndexLookup unavailable() {
        return new CardBinRangeIndexLookup(false, null);
    }

    /**
     * 构造索引可用结果；{@code result} 为空表示合法未命中，而非索引故障。
     *
     * @param result 活动快照的 BIN 命中结果，合法未命中时为空
     * @return 可用状态及其命中或未命中结果
     */
    public static CardBinRangeIndexLookup available(CardBinLookupResult result) {
        return new CardBinRangeIndexLookup(true, result);
    }
}
