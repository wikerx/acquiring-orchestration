package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import com.scott.payment.payment.mapper.PaymentCardBinRangeMapper;
import com.scott.payment.payment.model.PaymentCardBinCacheEntry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 使用 Spring Cache 按 11 位卡号前缀保存永久 BIN 查询结果。 */
@Service
public class PaymentCardBinCacheReader {

    private static final int MIN_BIN_LENGTH = 6;
    private static final int MAX_BIN_LENGTH = 11;

    private final PaymentCardBinRangeMapper cardBinRangeMapper;

    public PaymentCardBinCacheReader(PaymentCardBinRangeMapper cardBinRangeMapper) {
        this.cardBinRangeMapper = cardBinRangeMapper;
    }

    @Cacheable(cacheNames = PaymentCacheNames.CARD_BIN, key = "#p0")
    public PaymentCardBinCacheEntry findByPrefix(String cardBinPrefix) {
        long numericValue = Long.parseLong(cardBinPrefix);
        PaymentCardBinRangeDO matched = cardBinRangeMapper.selectBestMatch(
                candidateStarts(cardBinPrefix),
                numericValue);
        if (matched == null) {
            return PaymentCardBinCacheEntry.miss(cardBinPrefix);
        }
        PaymentCardBinCacheEntry entry = new PaymentCardBinCacheEntry();
        entry.setCardBinPrefix(cardBinPrefix);
        entry.setMatched(Boolean.TRUE);
        entry.setRangeId(matched.getId());
        entry.setBinLength(matched.getBinLength());
        entry.setCardBrand(matched.getCardBrand());
        entry.setIssuerCountryAlpha2(matched.getIssuerCountryAlpha2());
        entry.setIssuerCountryAlpha3(matched.getIssuerCountryAlpha3());
        entry.setIssuerCountryName(matched.getIssuerCountryName());
        return entry;
    }

    private List<Long> candidateStarts(String cardBinPrefix) {
        List<Long> candidates = new ArrayList<>(MAX_BIN_LENGTH - MIN_BIN_LENGTH + 1);
        for (int binLength = MIN_BIN_LENGTH; binLength <= MAX_BIN_LENGTH; binLength++) {
            String candidate = cardBinPrefix.substring(0, binLength)
                    + "0".repeat(MAX_BIN_LENGTH - binLength);
            candidates.add(Long.parseLong(candidate));
        }
        return candidates;
    }
}
