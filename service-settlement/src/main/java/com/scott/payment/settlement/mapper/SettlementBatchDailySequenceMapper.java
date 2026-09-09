package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementBatchDailySequenceDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchDailySequenceMapper
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次数据库日序列 Mapper；只允许主库事务内初始化、行锁读取和版本 CAS 递增。
 * @status : create
 */
public interface SettlementBatchDailySequenceMapper {

    /** 幂等初始化当日序列，不吞并其它表或字段错误。 */
    @Insert("""
            INSERT INTO settlement_batch_daily_sequence
            (business_date, current_sequence, version, create_time, update_time)
            VALUES (#{businessDate}, 0, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE business_date = business_date
            """)
    int insertIfAbsent(@Param("businessDate") LocalDate businessDate);

    /** 锁定当日序列，锁必须保持到批次插入事务提交。 */
    @Select("""
            SELECT business_date, current_sequence, version, create_time, update_time
            FROM settlement_batch_daily_sequence
            WHERE business_date = #{businessDate}
            FOR UPDATE
            """)
    SettlementBatchDailySequenceDO selectForUpdate(@Param("businessDate") LocalDate businessDate);

    /** 使用当前序号和版本双重 CAS 递增，最大值后拒绝继续发号。 */
    @Update("""
            UPDATE settlement_batch_daily_sequence
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
