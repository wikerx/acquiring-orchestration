package com.scott.payment.settlement.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 结算读取单个清分修订的精确季度路由键；三个字段必须共同出现在每个 SQL OR 分支。
 * @param transactionId 动作级交易号
 * @param transactionDateTime 动作真实季度分片时间
 * @param clearingRevision 清分修订号
 */
public record SettlementClearingLocator(String transactionId,
                                        LocalDateTime transactionDateTime,
                                        int clearingRevision) {

    public SettlementClearingLocator {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("settlement clearing transaction id is required");
        }
        transactionId = transactionId.trim();
        Objects.requireNonNull(transactionDateTime, "settlement clearing transaction time is required");
        if (clearingRevision < 1) {
            throw new IllegalArgumentException("settlement clearing revision must be positive");
        }
    }
}
