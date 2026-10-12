package com.scott.payment.component.db.reference.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinRangeIndexLookup
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : Card BIN Redis 快照读取状态，区分索引不可用与索引中的权威未命中
 * @status : create
 *
 * @param available 活动快照是否可读
 * @param result 活动快照给出的命中或未命中结果；不可用时为空
 */
public record CardBinRangeIndexLookup(boolean available, CardBinLookupResult result) {

    public static CardBinRangeIndexLookup unavailable() {
        return new CardBinRangeIndexLookup(false, null);
    }

    public static CardBinRangeIndexLookup available(CardBinLookupResult result) {
        return new CardBinRangeIndexLookup(true, result);
    }
}
