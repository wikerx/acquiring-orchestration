package com.scott.payment.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 按卡号前 11 位缓存的 BIN 查询结果；matched=false 用于避免数据库未命中时重复查询。 */
@Data
public class PaymentCardBinCacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cardBinPrefix;
    private Boolean matched;
    private Long rangeId;
    private Integer binLength;
    private String cardBrand;
    private String issuerCountryAlpha2;
    private String issuerCountryAlpha3;
    private String issuerCountryName;
    /** 当前命中区间的生效时间，用于缓存命中后的时间边界自检。 */
    private LocalDateTime effectiveTime;
    /** 当前命中区间的失效时间，达到该时间后必须回源主库。 */
    private LocalDateTime expireTime;
    /** miss 时可能匹配区间的最近未来生效时间，达到该时间后必须回源主库。 */
    private LocalDateTime nextEffectiveTime;

    public static PaymentCardBinCacheEntry miss(String cardBinPrefix) {
        PaymentCardBinCacheEntry entry = new PaymentCardBinCacheEntry();
        entry.setCardBinPrefix(cardBinPrefix);
        entry.setMatched(Boolean.FALSE);
        return entry;
    }

    /**
     * 判断缓存条目在指定业务时间是否仍可使用。
     *
     * @param now 平台当前时间
     * @return 未跨越命中失效点或 miss 的下一生效点时返回 true
     */
    public boolean usableAt(LocalDateTime now) {
        if (now == null) {
            return false;
        }
        if (Boolean.TRUE.equals(matched)) {
            return (effectiveTime == null || !now.isBefore(effectiveTime))
                    && (expireTime == null || now.isBefore(expireTime));
        }
        return nextEffectiveTime == null || now.isBefore(nextEffectiveTime);
    }
}
