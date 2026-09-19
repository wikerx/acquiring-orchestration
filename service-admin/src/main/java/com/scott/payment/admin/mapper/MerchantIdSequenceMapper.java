package com.scott.payment.admin.mapper;

import com.scott.payment.admin.entity.merchant.MerchantIdSequenceDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIdSequenceMapper
 * @date : 2026-09-17 18:10
 * @email : scott_x@163.com
 * @description : 商户号年度序列 Mapper；只负责幂等初始化、行锁读取和带版本条件的原子递增。
 * @status : create
 */
public interface MerchantIdSequenceMapper {

    /**
     * 幂等初始化指定年份的商户号序列。
     *
     * @param businessYear 两位业务年份
     * @return 新增或命中已有记录后的受影响行数
     */
    @Insert("""
            INSERT INTO base_merchant_id_sequence
            (business_year, current_sequence, version, gmt_create, gmt_modified)
            VALUES (#{businessYear}, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE business_year = business_year
            """)
    int insertIfAbsent(@Param("businessYear") String businessYear);

    /**
     * 锁定指定年份的序列记录，行锁必须保持到商户资料插入事务提交。
     *
     * @param businessYear 两位业务年份
     * @return 已锁定的年度序列
     */
    @Select("""
            SELECT business_year, current_sequence, version, gmt_create, gmt_modified
            FROM base_merchant_id_sequence
            WHERE business_year = #{businessYear}
            FOR UPDATE
            """)
    MerchantIdSequenceDO selectForUpdate(@Param("businessYear") String businessYear);

    /**
     * 在当前序号和版本均匹配时递增年度流水，超过四位容量后拒绝继续发号。
     *
     * @param businessYear 两位业务年份
     * @param expectedSequence 锁读到的当前流水
     * @param expectedVersion 锁读到的当前版本
     * @return 更新行数，正常情况下必须为 1
     */
    @Update("""
            UPDATE base_merchant_id_sequence
            SET current_sequence = current_sequence + 1,
                version = version + 1,
                gmt_modified = CURRENT_TIMESTAMP(3)
            WHERE business_year = #{businessYear}
              AND current_sequence = #{expectedSequence}
              AND current_sequence < 9999
              AND version = #{expectedVersion}
            """)
    int increment(@Param("businessYear") String businessYear,
                  @Param("expectedSequence") int expectedSequence,
                  @Param("expectedVersion") long expectedVersion);
}
