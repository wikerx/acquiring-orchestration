package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewDailySequenceDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewDailySequenceMapper
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 预审单号日序列数据访问接口；按结算业务日期锁行并以当前值和版本 CAS 递增。
 * @status : create
 */
public interface SettlementReviewDailySequenceMapper {

    /**
     * 初始化指定结算业务日期的预审序列行，重复调用不改变现值。
     * @param businessDate 结算业务日期
     * @return 新插入行数，已存在时为 0
     */
    @Insert("""
            INSERT INTO settlement_review_daily_sequence
            (business_date, current_sequence, version, create_time, update_time)
            VALUES (#{businessDate}, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE business_date = business_date
            """)
    int insertIfAbsent(@Param("businessDate") LocalDate businessDate);

    /**
     * 锁定指定日期序列行供当前事务分配下一预审编号。
     * @param businessDate 结算业务日期
     * @return 已加行锁的当前序列和版本，不存在时为空
     */
    @Select("""
            SELECT business_date, current_sequence, version
            FROM settlement_review_daily_sequence
            WHERE business_date = #{businessDate}
            FOR UPDATE
            """)
    SettlementReviewDailySequenceDO selectForUpdate(@Param("businessDate") LocalDate businessDate);

    /**
     * 以当前序号和版本双条件 CAS 递增，且拒绝超过八位上限。
     * @param businessDate 结算业务日期
     * @param expectedSequence 锁读到的当前序号
     * @param expectedVersion 锁读到的当前版本
     * @return 成功更新行数，非 1 表示并发冲突或序列耗尽
     */
    @Update("""
            UPDATE settlement_review_daily_sequence
            SET current_sequence = current_sequence + 1,
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE business_date = #{businessDate}
              AND current_sequence = #{expectedSequence}
              AND current_sequence < 99999999
              AND version = #{expectedVersion}
            """)
    int increment(@Param("businessDate") LocalDate businessDate,
                  @Param("expectedSequence") int expectedSequence,
                  @Param("expectedVersion") long expectedVersion);
}
