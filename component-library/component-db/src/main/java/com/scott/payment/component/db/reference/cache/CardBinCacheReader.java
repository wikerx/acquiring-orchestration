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
 * @description : 支付与 OpenAPI 共用的 Card BIN 查询入口，优先读取数据库有界 A/B 快照，故障时执行一次 SLAVE SQL
 * @status : update
 */
@Service
public class CardBinCacheReader {

    /** SLAVE 精确查询入口，仅在共享快照不可用时回源。 */
    private final ReferenceDataLookupService lookupService;

    /** 数据库记录驱动的共享快照；未装配 Redis 组件时为空。 */
    private final Optional<CardBinRangeIndex> rangeIndex;

    /**
     * 创建共享 Card BIN 查询入口。
     *
     * @param lookupService Card BIN 从库查询服务
     * @param rangeIndex Card BIN Redis A/B 快照，可选
     */
    public CardBinCacheReader(ReferenceDataLookupService lookupService,
                              Optional<CardBinRangeIndex> rangeIndex) {
        this.lookupService = lookupService;
        this.rangeIndex = rangeIndex;
    }

    /**
     * 查询规范化后的十一位 BIN。
     *
     * <p>活动快照可读时，六位桶不存在或桶内没有有效区间均作为权威未命中返回，不会创建
     * 查询值对应的永久 Key。只有 Redis 不可用、载荷损坏或快照尚未初始化时才执行现有
     * SLAVE SQL；数据库未命中仍由支付或 OpenAPI 上层执行统一卡品牌规则兜底。</p>
     *
     * @param cardBinPrefix 十一位数字前缀
     * @return 当前有效的数据库命中或未命中结果
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
        // 快照不可用时只回源一次；不把任意查询值写入 Redis，避免恶意请求扩大 Key 基数。
        return CardBinLookupCacheEntry.from(
                lookupService.lookupCardBinSnapshot(cardBinPrefix),
                null
        );
    }

    /**
     * 支付侧统一的查询精度：不足 6 位拒绝，6 至 10 位右补零，超过 11 位只取前 11 位。
     * 调用方不得把原始完整卡号写入日志或缓存。
     *
     * @param cardDigits 纯数字 BIN 或卡号
     * @return 用于共享快照和数据库查询的 11 位前缀
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
