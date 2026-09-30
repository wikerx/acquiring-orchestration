package com.scott.payment.component.db.reference.model;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinLookupSnapshot
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 卡 BIN 查询结果及其时间边界，供支付与 OpenAPI 缓存避免跨越区间失效或未来生效点。
 * @status : create
 *
 * @param result 当前查询结果
 * @param expireTime 命中区间的失效时刻，允许为空
 * @param nextEffectiveTime 同一 BIN 最近未来生效时刻，允许为空
 */
public record CardBinLookupSnapshot(CardBinLookupResult result,
                                    LocalDateTime expireTime,
                                    LocalDateTime nextEffectiveTime) {
}
