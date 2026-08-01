package com.scott.payment.risk.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 风控运行时缓存条目。
 *
 * <p>使用明确的 found 状态表达负缓存，避免将 {@code __MISS__} 等魔法字符串与业务对象混存。</p>
 */
@Data
public class RiskRuntimeCacheEntry {

    /**
     * 数据库是否存在匹配结果。
     */
    private boolean found;

    /**
     * 单条规则或名单匹配结果。
     */
    private RiskListMatch match;

    /**
     * 规则集合，当前用于交易频率规则快照。
     */
    private List<RiskListMatch> matches = new ArrayList<>();

    /**
     * 创建单条规则缓存结果，用 found 明确区分负缓存和 Redis 读取异常。
     *
     * @param value 匹配结果，允许为空
     * @return 单条缓存条目
     */
    public static RiskRuntimeCacheEntry match(RiskListMatch value) {
        RiskRuntimeCacheEntry entry = new RiskRuntimeCacheEntry();
        entry.setFound(value != null);
        entry.setMatch(value);
        return entry;
    }

    /**
     * 创建规则集合缓存结果并复制为不可变列表，防止调用方修改缓存快照。
     *
     * @param values 规则集合，允许为空
     * @return 集合缓存条目
     */
    public static RiskRuntimeCacheEntry matches(List<RiskListMatch> values) {
        RiskRuntimeCacheEntry entry = new RiskRuntimeCacheEntry();
        List<RiskListMatch> safeValues = values == null ? List.of() : List.copyOf(values);
        entry.setFound(!safeValues.isEmpty());
        entry.setMatches(safeValues);
        return entry;
    }
}
