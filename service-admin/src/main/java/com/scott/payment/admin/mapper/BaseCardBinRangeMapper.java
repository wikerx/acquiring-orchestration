package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.base.CardBinEntities;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 卡 BIN 区间 Mapper，位于 service-admin 数据访问层，负责基础卡 BIN 区间表的持久化访问。
 */
public interface BaseCardBinRangeMapper extends BaseMapper<CardBinEntities.BaseCardBinRangeDO> {

    /**
     * 判断当前数据库是否存在指定表。
     *
     * @param tableName 表名
     * @return 表数量，存在时为 1
     */
    @Select("SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = #{tableName}")
    int countTable(@Param("tableName") String tableName);

    /**
     * 查询旧卡 BIN 表总行数。
     *
     * @return 旧表总行数
     */
    @Select("SELECT COUNT(1) FROM card_bin_type_info")
    int countLegacyRows();

    /**
     * 查询旧卡 BIN 表中无效行数。
     *
     * @return 无效行数
     */
    @Select("""
            SELECT COUNT(1)
            FROM card_bin_type_info
            WHERE card_bin_start IS NULL
               OR card_bin_end IS NULL
               OR card_bin_start > card_bin_end
            """)
    int countInvalidLegacyRows();

    /**
     * 使用集合式 SQL 导入尚未迁移的有效旧卡 BIN 数据。
     *
     * <p>{@code INSERT IGNORE} 与现有唯一键共同处理并发导入竞态，返回值只统计本批次真实新增行。</p>
     *
     * @param batchNo 本次导入批次号
     * @param operator 当前操作人
     * @return 本批次真实新增行数
     */
    @Insert("""
            INSERT IGNORE INTO base_card_bin_range (
                legacy_pk_id, card_bin_start, card_bin_end, bin_length, card_brand, card_sub_brand,
                card_type, card_level, issuer_country_name, issuer_country_alpha2,
                issuer_country_alpha3, issuer_country_numeric, issuer_bank, issuer_web_url,
                issuer_telephone, data_source, source_batch_no, source_priority, effective_time,
                expire_time, status, remark, create_by, update_by, create_time, update_time, deleted
            )
            SELECT
                legacy.pk_id,
                legacy.card_bin_start,
                legacy.card_bin_end,
                CASE
                    WHEN MOD(legacy.card_bin_start, 100000) = 0
                         AND MOD(legacy.card_bin_end, 100000) = 99999
                         AND FLOOR(legacy.card_bin_start / 100000) = FLOOR(legacy.card_bin_end / 100000) THEN 6
                    WHEN MOD(legacy.card_bin_start, 10000) = 0
                         AND MOD(legacy.card_bin_end, 10000) = 9999
                         AND FLOOR(legacy.card_bin_start / 10000) = FLOOR(legacy.card_bin_end / 10000) THEN 7
                    WHEN MOD(legacy.card_bin_start, 1000) = 0
                         AND MOD(legacy.card_bin_end, 1000) = 999
                         AND FLOOR(legacy.card_bin_start / 1000) = FLOOR(legacy.card_bin_end / 1000) THEN 8
                    WHEN MOD(legacy.card_bin_start, 100) = 0
                         AND MOD(legacy.card_bin_end, 100) = 99
                         AND FLOOR(legacy.card_bin_start / 100) = FLOOR(legacy.card_bin_end / 100) THEN 9
                    WHEN MOD(legacy.card_bin_start, 10) = 0
                         AND MOD(legacy.card_bin_end, 10) = 9
                         AND FLOOR(legacy.card_bin_start / 10) = FLOOR(legacy.card_bin_end / 10) THEN 10
                    ELSE 11
                END,
                COALESCE(NULLIF(UPPER(TRIM(legacy.card_brand)), ''), 'UNKNOWN'),
                NULLIF(TRIM(legacy.card_sub_brand), ''),
                CASE UPPER(TRIM(legacy.credit_debit))
                    WHEN 'CREDIT' THEN 'CREDIT'
                    WHEN 'DEBIT' THEN 'DEBIT'
                    WHEN 'PREPAID' THEN 'PREPAID'
                    WHEN 'CHARGE' THEN 'CHARGE'
                    WHEN 'COMMERCIAL' THEN 'COMMERCIAL'
                    ELSE 'UNKNOWN'
                END,
                NULL,
                NULLIF(TRIM(legacy.issuer_country_name), ''),
                NULLIF(UPPER(TRIM(legacy.issuer_country_code_ii)), ''),
                NULLIF(UPPER(TRIM(legacy.issuer_country_code)), ''),
                NULLIF(TRIM(legacy.issuer_country_number), ''),
                NULLIF(TRIM(legacy.issuer_bank), ''),
                NULLIF(TRIM(legacy.issuer_web_url), ''),
                NULLIF(TRIM(legacy.issuer_telephone), ''),
                'LEGACY_DB',
                #{batchNo},
                50,
                NULL,
                NULL,
                1,
                '旧表card_bin_type_info初始化导入',
                #{operator},
                #{operator},
                COALESCE(legacy.gmt_create, NOW(3)),
                COALESCE(legacy.gmt_modified, NOW(3)),
                0
            FROM card_bin_type_info legacy
            WHERE legacy.card_bin_start IS NOT NULL
              AND legacy.card_bin_end IS NOT NULL
              AND legacy.card_bin_start <= legacy.card_bin_end
              AND NOT EXISTS (
                  SELECT 1
                  FROM base_card_bin_range current_row
                  WHERE current_row.data_source = 'LEGACY_DB'
                    AND current_row.legacy_pk_id = legacy.pk_id
                    AND current_row.deleted = 0
              )
            """)
    int insertValidLegacyRows(@Param("batchNo") String batchNo, @Param("operator") String operator);
}
