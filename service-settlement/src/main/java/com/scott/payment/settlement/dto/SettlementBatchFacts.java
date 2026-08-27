package com.scott.payment.settlement.dto;

import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchFacts
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 单批次完整不可变输入，聚合候选、交易清分、保证金清分及汇率矩阵所需币种集合。
 * @status : create
 * @param candidates 本批已认领候选
 * @param transactionDetails 交易本金和费用清分事实
 * @param reserveDetails 独立保证金清分事实
 * @param currencies 本批全部原币种及 exponent
 */
public record SettlementBatchFacts(List<SettlementCandidateDO> candidates,
                                   List<SettlementTransactionClearingDetailDO> transactionDetails,
                                   List<SettlementReserveClearingDetailDO> reserveDetails,
                                   Set<SettlementCurrency> currencies) {

    public SettlementBatchFacts {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "settlement candidates are required"));
        transactionDetails = List.copyOf(Objects.requireNonNull(
                transactionDetails, "transaction clearing facts are required"));
        reserveDetails = List.copyOf(Objects.requireNonNull(
                reserveDetails, "reserve clearing facts are required"));
        currencies = Set.copyOf(Objects.requireNonNull(currencies, "settlement currencies are required"));
    }
}
