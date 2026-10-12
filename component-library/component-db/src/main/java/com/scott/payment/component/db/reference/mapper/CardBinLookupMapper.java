package com.scott.payment.component.db.reference.mapper;

import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.entity.CardBinIndexSourceSignatureDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

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
     * 从 SLAVE 按主键稳定分页读取构建 Redis 快照所需的全部区间字段。
     */
    @Select("""
            SELECT id,
                   card_bin_start AS cardBinStart,
                   card_bin_end AS cardBinEnd,
                   bin_length AS binLength,
                   card_brand AS cardBrand,
                   card_sub_brand AS cardSubBrand,
                   card_type AS cardType,
                   card_level AS cardLevel,
                   issuer_country_name AS issuerCountryName,
                   issuer_country_alpha2 AS issuerCountryAlpha2,
                   issuer_country_alpha3 AS issuerCountryAlpha3,
                   issuer_country_numeric AS issuerCountryNumeric,
                   issuer_bank AS issuerBank,
                   effective_time AS effectiveTime,
                   expire_time AS expireTime,
                   update_time AS updateTime
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND id > #{lastId}
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<CardBinRangeDO> selectIndexPage(@Param("lastId") long lastId,
                                         @Param("limit") int limit);

    /**
     * 计算当前从库快照源签名；构建前后结果必须一致才允许激活新槽。
     */
    @Select("""
            SELECT COUNT(*) AS rowCount,
                   COALESCE(BIT_XOR(CRC32(CONCAT_WS('|',
                       id, card_bin_start, card_bin_end, bin_length,
                       COALESCE(card_brand, ''), COALESCE(card_sub_brand, ''),
                       COALESCE(card_type, ''), COALESCE(card_level, ''),
                       COALESCE(issuer_country_name, ''), COALESCE(issuer_country_alpha2, ''),
                       COALESCE(issuer_country_alpha3, ''), COALESCE(issuer_country_numeric, ''),
                       COALESCE(issuer_bank, ''),
                       COALESCE(DATE_FORMAT(effective_time, '%Y-%m-%d %H:%i:%s.%f'), ''),
                       COALESCE(DATE_FORMAT(expire_time, '%Y-%m-%d %H:%i:%s.%f'), ''),
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s.%f')
                   ))), 0) AS checksum
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            """)
    CardBinIndexSourceSignatureDO selectIndexSourceSignature();

    /**
     * 查询不超过商户输入精度的当前有效最优 BIN 区间。
     * 起点右补 0、终点右补 9 后存为 11 位数值；区间可能跨多个 BIN，
     * 因此必须用包含关系，不能只按候选起点相等匹配。
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
                   issuer_bank AS issuerBank,
                   expire_time AS expireTime
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

    /**
     * 查询同一输入可能命中的最近未来生效时刻，供正向缓存提前失效。
     *
     * @param numericValue 右补零到 11 位的 BIN 数值
     * @param inputLength 商户输入长度
     * @return 最近未来生效时刻；不存在时返回 null
     */
    @Select("""
            SELECT MIN(effective_time)
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND bin_length <= #{inputLength}
              AND card_bin_start <= #{numericValue}
              AND card_bin_end >= #{numericValue}
              AND effective_time > CURRENT_TIMESTAMP(3)
              AND (expire_time IS NULL OR expire_time > effective_time)
            """)
    LocalDateTime selectNextEffectiveTime(@Param("numericValue") long numericValue,
                                          @Param("inputLength") int inputLength);
}
