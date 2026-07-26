package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.base.CardBinEntities;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseCardBinRangeMapper
 * @date : 2026-07-04 00:00
 * @email : scott_x@163.com
 * @description : 卡 BIN 区间 Mapper，位于 service-admin 数据访问层，负责基础卡 BIN 区间表的持久化访问。
 * @status : create
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
    /**
     * 完成 count Invalid Legacy Rows 分支的校验或转换，返回值供当前调用链继续组装结果。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    int countInvalidLegacyRows();

    /**
     * 查询旧卡 BIN 表中的有效数据。
     *
     * @return 旧卡 BIN 有效行
     */
    @Select("""
            SELECT
                pk_id AS legacyPkId,
                card_bin_start AS cardBinStart,
                card_bin_end AS cardBinEnd,
                credit_debit AS creditDebit,
                issuer_country_name AS issuerCountryName,
                issuer_country_code_ii AS issuerCountryAlpha2,
                issuer_country_code AS issuerCountryAlpha3,
                issuer_country_number AS issuerCountryNumeric,
                issuer_bank AS issuerBank,
                issuer_web_url AS issuerWebUrl,
                issuer_telephone AS issuerTelephone,
                card_brand AS cardBrand,
                card_sub_brand AS cardSubBrand,
                gmt_create AS createTime,
                gmt_modified AS updateTime
            FROM card_bin_type_info
            WHERE card_bin_start IS NOT NULL
              AND card_bin_end IS NOT NULL
              AND card_bin_start <= card_bin_end
            ORDER BY pk_id ASC
            """)
    /**
     * 查询 select Valid Legacy Rows 所需数据，未命中时按调用场景返回空值或抛出异常。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 解析或查询得到的业务值
     */
    List<Map<String, Object>> selectValidLegacyRows();
}
