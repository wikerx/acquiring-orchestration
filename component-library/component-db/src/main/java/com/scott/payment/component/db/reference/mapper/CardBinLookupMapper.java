package com.scott.payment.component.db.reference.mapper;

import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinLookupMapper
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : 卡 BIN 公共只读查询 Mapper，按输入精度、生效状态和更新时间返回单条最优匹配
 * @status : create
 */
@Mapper
public interface CardBinLookupMapper {

    /**
     * 查询不超过商户输入精度的当前有效最优 BIN 区间。
     *
     * @param numericValue 右侧补零到 11 位后的 BIN 数值
     * @param inputLength  商户输入长度，范围为 6 至 11
     * @return 最优匹配记录，未命中返回 null
     */
    @Select("""
            SELECT bin_length AS binLength,
                   card_brand AS cardBrand,
                   card_sub_brand AS cardSubBrand,
                   card_type AS cardType,
                   card_level AS cardLevel,
                   issuer_country_name AS issuerCountryName,
                   issuer_country_alpha2 AS issuerCountryAlpha2,
                   issuer_country_alpha3 AS issuerCountryAlpha3,
                   issuer_country_numeric AS issuerCountryNumeric,
                   issuer_bank AS issuerBank
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND bin_length <= #{inputLength}
              AND card_bin_start <= #{numericValue}
              AND card_bin_end >= #{numericValue}
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            ORDER BY bin_length DESC, update_time DESC, id DESC
            LIMIT 1
            """)
    CardBinRangeDO selectBestMatch(@Param("numericValue") long numericValue,
                                   @Param("inputLength") int inputLength);
}
