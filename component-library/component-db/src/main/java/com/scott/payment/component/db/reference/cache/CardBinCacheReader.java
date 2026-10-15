package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.db.reference.model.CardBinLookupCacheEntry;
import com.scott.payment.component.db.reference.model.CardBinRangeIndexLookup;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinCacheReader
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 支付与 OpenAPI 共用的 Card BIN 查询入口，优先读取数据库有界三位分片索引，故障时执行一次 SLAVE SQL
 * @status : update
 */
@Service
public class CardBinCacheReader {

    /** SLAVE 精确查询入口，仅在共享分片索引不可用时回源。 */
    private final ReferenceDataLookupService lookupService;

    /** 数据库记录驱动的共享分片索引；未装配 Redis 组件时为空。 */
    private final Optional<CardBinRangeIndex> rangeIndex;

    /**
     * 创建共享 Card BIN 查询入口。
     *
     * @param lookupService Card BIN 从库查询服务
     * @param rangeIndex Card BIN Redis 三位分片索引，可选
     */
    public CardBinCacheReader(ReferenceDataLookupService lookupService,
                              Optional<CardBinRangeIndex> rangeIndex) {
        this.lookupService = lookupService;
        this.rangeIndex = rangeIndex;
    }

    /**
     * 查询规范化后的十一位 BIN。
     *
     * <p>当前 generation 的分片完整时，全部前缀均不存在或候选内没有覆盖区间即为权威未命中，不会创建
     * 查询值对应的永久 Key。只有发布中、Redis 不可用、载荷损坏或分片尚未初始化时才执行现有
     * SLAVE SQL；数据库未命中仍由支付或 OpenAPI 上层执行统一卡品牌规则兜底。</p>
     *
     * @param cardBinPrefix 十一位数字前缀
     * @return 当前启用记录中的数据库命中或未命中结果
     */
    public CardBinLookupCacheEntry findByPrefix(String cardBinPrefix) {
        if (cardBinPrefix == null || !cardBinPrefix.matches("^[0-9]{11}$")) {
            throw new IllegalArgumentException("cardBinPrefix must be 11 digits");
        }
        if (rangeIndex.isPresent()) {
            CardBinRangeIndexLookup indexed = rangeIndex.get().lookup(cardBinPrefix);
            if (indexed.available()) {
                return new CardBinLookupCacheEntry(
                        "range-index-v1",
                        CardBinLookupCacheEntry.FORMAT_VERSION,
                        indexed.result(),
                        null
                );
            }
        }
        // 分片不可用时只回源一次；不把任意查询值写入 Redis，避免恶意请求扩大 Key 基数。
        return new CardBinLookupCacheEntry(
                null,
                CardBinLookupCacheEntry.FORMAT_VERSION,
                lookupService.lookupCardBin(cardBinPrefix),
                null
        );
    }

    /**
     * 支付侧统一的查询精度：不足 6 位拒绝，6 至 10 位右补零，超过 11 位只取前 11 位。
     * 调用方不得把原始完整卡号写入日志或缓存。
     *
     * @param cardDigits 纯数字 BIN 或卡号
     * @return 用于共享分片索引和数据库查询的 11 位前缀
     * @throws IllegalArgumentException 输入不足 6 位或包含非数字字符时抛出
     */
    public static String toElevenDigitPrefix(String cardDigits) {
        if (cardDigits == null || cardDigits.length() < 6
                || !cardDigits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("cardBin must contain at least 6 digits");
        }
        return cardDigits.length() >= 11
                ? cardDigits.substring(0, 11)
                : cardDigits + "0".repeat(11 - cardDigits.length());
    }
}
