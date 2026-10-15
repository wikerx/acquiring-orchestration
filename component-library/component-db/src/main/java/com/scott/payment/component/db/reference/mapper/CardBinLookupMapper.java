package com.scott.payment.component.db.reference.mapper;

import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.entity.CardBinIndexSourceSignatureDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinLookupMapper
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : 卡 BIN 公共只读查询 Mapper，统一按十一位数值区间和最大主键返回单条匹配
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
                   issuer_bank AS issuerBank
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND id > #{lastId}
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
                       COALESCE(issuer_bank, '')
                   ))), 0) AS checksum
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
            """)
    CardBinIndexSourceSignatureDO selectIndexSourceSignature();

    /**
     * 查询覆盖十一位 BIN 数值的最新启用区间。
     *
     * <p>管理端把区间起点和终点统一保存为十一位数值。运行时只判断区间包含关系；
     * 当多条启用记录重叠时，运维约定增量记录的主键越大优先级越高，因此固定按
     * {@code id DESC LIMIT 1} 选择唯一结果。逻辑删除和停用记录不得参与匹配。</p>
     *
     * @param numericValue 右侧补零到 11 位后的 BIN 数值
     * @return 主键最大的覆盖记录，未命中返回 null
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
                   issuer_bank AS issuerBank
            FROM base_card_bin_range
            WHERE deleted = 0
              AND status = 1
              AND card_bin_start <= #{numericValue}
              AND card_bin_end >= #{numericValue}
            ORDER BY id DESC
            LIMIT 1
            """)
    CardBinRangeDO selectBestMatch(@Param("numericValue") long numericValue);
}
