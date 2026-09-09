package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.settlement.core.SettlementRateNormalizer;
import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateQuote;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.entity.SettlementRateQuoteDO;
import com.scott.payment.settlement.mapper.SettlementRateQuoteMapper;
import com.scott.payment.settlement.service.SettlementRateResolutionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementRateResolutionService
 * @date : 2026-08-26 22:40
 * @email : scott_x@163.com
 * @description : 一次批量查询全部跨币种报价，按默认来源、优先级、方向、生效时间和主键确定唯一批次直接汇率。
 * @status : create
 */
@Service
public class DefaultSettlementRateResolutionService implements SettlementRateResolutionService {

    private final SettlementRateQuoteMapper quoteMapper;
    private final SettlementRateNormalizer normalizer = new SettlementRateNormalizer();

    public DefaultSettlementRateResolutionService(SettlementRateQuoteMapper quoteMapper) {
        this.quoteMapper = quoteMapper;
    }

    /**
     * 同币种不访问业务汇率表；跨币种缺少有效报价时明确失败，禁止默认汇率 1。
     *
     * @param currencies 原币种集合
     * @param targetCurrency 目标币种
     * @param targetCurrencyExponent 目标 exponent
     * @param valuationTime 统一估值时间
     * @return 完整汇率矩阵
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public RateMatrix resolve(Set<SettlementCurrency> currencies,
                              String targetCurrency,
                              int targetCurrencyExponent,
                              LocalDateTime valuationTime) {
        Objects.requireNonNull(currencies, "settlement currencies are required");
        Objects.requireNonNull(valuationTime, "settlement rate valuation time is required");
        SettlementCurrency target = new SettlementCurrency(targetCurrency, targetCurrencyExponent);
        Map<String, Integer> exponents = validateExponents(currencies, target);
        List<CurrencyPair> crossPairs = exponents.keySet().stream()
                .filter(currency -> !currency.equals(target.currency()))
                .sorted()
                .map(currency -> new CurrencyPair(currency, target.currency()))
                .toList();
        List<SettlementRateQuoteDO> quotes = crossPairs.isEmpty()
                ? List.of() : quoteMapper.selectEffectiveQuotes(crossPairs, valuationTime);

        List<LockedRate> locked = new ArrayList<>(exponents.size());
        exponents.keySet().stream().sorted().forEach(sourceCurrency -> {
            int sourceExponent = exponents.get(sourceCurrency);
            if (sourceCurrency.equals(target.currency())) {
                locked.add(normalizer.identity(sourceCurrency, sourceExponent, valuationTime));
            } else {
                locked.add(resolveCross(sourceCurrency, sourceExponent, target, quotes));
            }
        });
        return RateMatrix.of(locked);
    }

    /** 校验请求币种均为规范 ISO 代码且同币种 exponent 唯一，并与目标 exponent 一致。 */
    private Map<String, Integer> validateExponents(Set<SettlementCurrency> currencies,
                                                   SettlementCurrency target) {
        Map<String, Integer> exponents = new HashMap<>();
        for (SettlementCurrency currency : currencies) {
            if (currency == null) {
                throw new IllegalArgumentException("settlement currency must not be null");
            }
            Integer previous = exponents.putIfAbsent(currency.currency(), currency.exponent());
            if (previous != null && previous != currency.exponent()) {
                throw new IllegalStateException("settlement currency exponent is inconsistent for "
                        + currency.currency());
            }
        }
        Integer targetPrevious = exponents.putIfAbsent(target.currency(), target.exponent());
        if (targetPrevious != null && targetPrevious != target.exponent()) {
            throw new IllegalStateException("settlement target currency exponent is inconsistent");
        }
        return exponents;
    }

    /** 从有效报价中确定性选择来源，归一正向/反向报价并保留原始方向和生效时间。 */
    private LockedRate resolveCross(String sourceCurrency,
                                    int sourceExponent,
                                    SettlementCurrency target,
                                    List<SettlementRateQuoteDO> quotes) {
        CurrencyPair pair = new CurrencyPair(sourceCurrency, target.currency());
        SettlementRateQuoteDO selected = quotes.stream()
                .filter(row -> matches(row, pair))
                .min(quoteComparator(pair))
                .orElseThrow(() -> new IllegalStateException(
                        "settlement rate is missing for " + pair.displayName()));
        QuoteDirection direction = isDirect(selected, pair)
                ? QuoteDirection.DIRECT : QuoteDirection.INVERSE;
        RateQuote quote = new RateQuote(
                pair,
                selected.getFinalRate(),
                direction,
                selected.getSourceCode(),
                String.valueOf(selected.getId()),
                selected.getEffectiveTime());
        return normalizer.normalize(quote, sourceExponent, target.exponent());
    }

    /** 按优先级、最新生效时间、报价 ID 建立确定性报价选择顺序。 */
    private Comparator<SettlementRateQuoteDO> quoteComparator(CurrencyPair pair) {
        return Comparator
                .comparingInt((SettlementRateQuoteDO row) -> Integer.valueOf(1).equals(row.getDefaultSource()) ? 0 : 1)
                .thenComparingInt(row -> row.getSourcePriority() == null
                        ? Integer.MAX_VALUE : row.getSourcePriority())
                .thenComparingInt(row -> isDirect(row, pair) ? 0 : 1)
                .thenComparing(SettlementRateQuoteDO::getEffectiveTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SettlementRateQuoteDO::getId,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /** 判断报价是否覆盖请求币种对，允许正向或反向原始报价。 */
    private boolean matches(SettlementRateQuoteDO row, CurrencyPair pair) {
        return row != null && row.getFinalRate() != null && row.getFinalRate().signum() > 0
                && row.getSourceCode() != null && row.getEffectiveTime() != null
                && (isDirect(row, pair) || (pair.targetCurrency().equals(row.getBaseCurrency())
                    && pair.sourceCurrency().equals(row.getQuoteCurrency())));
    }

    /** 判断原始报价方向是否与 sourceCurrency 到 targetCurrency 一致。 */
    private boolean isDirect(SettlementRateQuoteDO row, CurrencyPair pair) {
        return pair.sourceCurrency().equals(row.getBaseCurrency())
                && pair.targetCurrency().equals(row.getQuoteCurrency());
    }
}
