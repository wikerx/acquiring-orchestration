package com.scott.payment.risk.mapper;

import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额预占数据访问。
 * @status : create
 */
public interface MerchantLimitReservationMapper {

    /**
     * 写入一条 PREPARING 状态的限额预占事实，业务唯一键冲突由上层按幂等语义处理。
     *
     * @param record 包含交易、规则、周期及六位小数整数金额单位的预占记录
     * @return 实际写入行数，成功时应为 1
     */
    @Insert("""
            INSERT INTO risk_merchant_limit_reservation
            (
              transaction_id, risk_record_no, merchant_id, rule_id, limit_type, currency,
              period_bucket, period_begin_time, period_end_time, amount_units, counter_mode,
              reservation_status, cancel_reason, expires_at, reserved_time, confirmed_time,
              cancelled_time, version, deleted, create_time, update_time
            )
            VALUES
            (
              #{record.transactionId}, #{record.riskRecordNo}, #{record.merchantId}, #{record.ruleId},
              #{record.limitType}, #{record.currency}, #{record.periodBucket}, #{record.periodBeginTime},
              #{record.periodEndTime}, #{record.amountUnits}, #{record.counterMode},
              #{record.reservationStatus}, #{record.cancelReason}, #{record.expiresAt},
              #{record.reservedTime}, #{record.confirmedTime}, #{record.cancelledTime},
              #{record.version}, #{record.deleted}, #{record.createTime}, #{record.updateTime}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id")
    int insertPreparing(@Param("record") MerchantLimitReservationDO record);

    /**
     * 按预占业务唯一键查询未删除记录，用于唯一键冲突后的幂等载荷核对。
     *
     * @param transactionId 平台交易号
     * @param ruleId 商户限额规则主键
     * @param limitType 累计限额周期类型
     * @param periodBucket 规则时区下的周期桶标识
     * @return 已存在的预占事实；不存在时返回 {@code null}
     */
    @Select("""
            SELECT *
            FROM risk_merchant_limit_reservation
            WHERE transaction_id = #{transactionId}
              AND rule_id = #{ruleId}
              AND limit_type = #{limitType}
              AND period_bucket = #{periodBucket}
              AND deleted = 0
            LIMIT 1
            """)
    MerchantLimitReservationDO selectByBusinessKey(@Param("transactionId") String transactionId,
                                                   @Param("ruleId") Long ruleId,
                                                   @Param("limitType") String limitType,
                                                   @Param("periodBucket") String periodBucket);

    /**
     * 查询指定交易的全部未删除预占事实，结果按主键升序保持稳定处理顺序。
     *
     * @param transactionId 平台交易号
     * @return 交易关联的预占记录
     */
    @Select("""
            SELECT *
            FROM risk_merchant_limit_reservation
            WHERE transaction_id = #{transactionId}
              AND deleted = 0
            ORDER BY id ASC
            """)
    List<MerchantLimitReservationDO> selectByTransactionId(@Param("transactionId") String transactionId);

    /**
     * 在当前事务中对指定交易的全部预占事实加行锁，防止确认和取消并发覆盖。
     *
     * @param transactionId 平台交易号
     * @return 已加行锁并按主键升序返回的预占记录
     */
    @Select("""
            SELECT *
            FROM risk_merchant_limit_reservation
            WHERE transaction_id = #{transactionId}
              AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<MerchantLimitReservationDO> selectByTransactionIdForUpdate(
            @Param("transactionId") String transactionId);

    /**
     * 按主键查询未删除的预占事实，用于 CAS 失败后的最新状态核对。
     *
     * @param id 预占记录主键
     * @return 最新预占事实；不存在时返回 {@code null}
     */
    @Select("""
            SELECT *
            FROM risk_merchant_limit_reservation
            WHERE id = #{id}
              AND deleted = 0
            LIMIT 1
            """)
    MerchantLimitReservationDO selectReservationById(@Param("id") Long id);

    /**
     * 使用源状态和版本号双重条件推进预占状态，并同步记录对应状态时间。
     *
     * @param id 预占记录主键
     * @param version 调用方读取到的乐观锁版本
     * @param sourceStatus 允许迁移的当前状态
     * @param targetStatus 目标状态，状态合法性由服务层预先校验
     * @param reason 取消原因，仅目标状态为 CANCELLED 时写入
     * @param now 本次迁移时间
     * @return 更新行数；0 表示状态或版本已被并发方改变
     */
    @Update("""
            UPDATE risk_merchant_limit_reservation
            SET reservation_status = #{targetStatus},
                reserved_time = CASE WHEN #{targetStatus} = 'RESERVED' THEN #{now} ELSE reserved_time END,
                confirmed_time = CASE WHEN #{targetStatus} = 'CONFIRMED' THEN #{now} ELSE confirmed_time END,
                cancelled_time = CASE WHEN #{targetStatus} = 'CANCELLED' THEN #{now} ELSE cancelled_time END,
                cancel_reason = CASE WHEN #{targetStatus} = 'CANCELLED' THEN #{reason} ELSE cancel_reason END,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND version = #{version}
              AND reservation_status = #{sourceStatus}
              AND deleted = 0
            """)
    int transitionStatus(@Param("id") Long id,
                         @Param("version") Integer version,
                         @Param("sourceStatus") String sourceStatus,
                         @Param("targetStatus") String targetStatus,
                         @Param("reason") String reason,
                         @Param("now") LocalDateTime now);

    /**
     * 查询长期停留在 PREPARING 或 RESERVED 的记录，供补偿任务核对数据库与 Redis。
     *
     * @param updatedBefore 最晚更新时间阈值
     * @param limit 单批最大返回数
     * @return 按更新时间和主键升序排列的非终态预占记录
     */
    @Select("""
            SELECT *
            FROM risk_merchant_limit_reservation
            WHERE deleted = 0
              AND reservation_status IN ('PREPARING', 'RESERVED')
              AND update_time <= #{updatedBefore}
            ORDER BY update_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<MerchantLimitReservationDO> selectStaleNonTerminal(@Param("updatedBefore") LocalDateTime updatedBefore,
                                                           @Param("limit") int limit);
}
