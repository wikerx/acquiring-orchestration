package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.exchange.ExchangeRateAlertEntities.ExchangeRateAlertEventDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 汇率波动预警事件数据访问接口，状态变化全部使用版本 CAS。 */
public interface ExchangeRateAlertEventMapper extends BaseMapper<ExchangeRateAlertEventDO> {

    /** 依靠活动键唯一索引尝试创建事件，重复触发返回 0。 */
    @Insert("""
            INSERT IGNORE INTO exchange_rate_alert_event (
                event_no, active_event_key, rule_id, rule_code, rule_name,
                rate_type, base_currency, quote_currency, alert_level,
                window_minutes, trigger_threshold, recovery_threshold,
                window_start_time, window_end_time, sample_count,
                minimum_rate, minimum_rate_source, minimum_business_rate_id,
                maximum_rate, maximum_rate_source, maximum_business_rate_id,
                latest_rate, latest_rate_source, latest_business_rate_id,
                volatility_rate, maximum_volatility_rate, recovery_streak,
                event_status, first_trigger_time, last_detected_time,
                version, create_time, update_time, deleted
            ) VALUES (
                #{eventNo}, #{activeEventKey}, #{ruleId}, #{ruleCode}, #{ruleName},
                #{rateType}, #{baseCurrency}, #{quoteCurrency}, #{alertLevel},
                #{windowMinutes}, #{triggerThreshold}, #{recoveryThreshold},
                #{windowStartTime}, #{windowEndTime}, #{sampleCount},
                #{minimumRate}, #{minimumRateSource}, #{minimumBusinessRateId},
                #{maximumRate}, #{maximumRateSource}, #{maximumBusinessRateId},
                #{latestRate}, #{latestRateSource}, #{latestBusinessRateId},
                #{volatilityRate}, #{maximumVolatilityRate}, #{recoveryStreak},
                #{eventStatus}, #{firstTriggerTime}, #{lastDetectedTime},
                #{version}, #{createTime}, #{updateTime}, #{deleted}
            )
            """)
    int insertActiveIgnore(ExchangeRateAlertEventDO row);

    /** 持续超阈时刷新活动事件证据并重置恢复计数。 */
    @Update("""
            UPDATE exchange_rate_alert_event
            SET window_start_time = #{row.windowStartTime},
                window_end_time = #{row.windowEndTime},
                sample_count = #{row.sampleCount},
                minimum_rate = #{row.minimumRate},
                minimum_rate_source = #{row.minimumRateSource},
                minimum_business_rate_id = #{row.minimumBusinessRateId},
                maximum_rate = #{row.maximumRate},
                maximum_rate_source = #{row.maximumRateSource},
                maximum_business_rate_id = #{row.maximumBusinessRateId},
                latest_rate = #{row.latestRate},
                latest_rate_source = #{row.latestRateSource},
                latest_business_rate_id = #{row.latestBusinessRateId},
                volatility_rate = #{row.volatilityRate},
                maximum_volatility_rate = GREATEST(maximum_volatility_rate, #{row.volatilityRate}),
                recovery_streak = 0,
                last_detected_time = #{row.lastDetectedTime},
                update_time = #{row.updateTime},
                version = version + 1
            WHERE id = #{row.id}
              AND version = #{row.version}
              AND active_event_key IS NOT NULL
              AND event_status IN ('OPEN', 'ACKNOWLEDGED')
            """)
    int updateTriggered(@Param("row") ExchangeRateAlertEventDO row);

    /** 低于恢复阈值但未达到连续次数时，仅推进恢复计数和窗口证据。 */
    @Update("""
            UPDATE exchange_rate_alert_event
            SET window_start_time = #{row.windowStartTime},
                window_end_time = #{row.windowEndTime},
                sample_count = #{row.sampleCount},
                minimum_rate = #{row.minimumRate},
                minimum_rate_source = #{row.minimumRateSource},
                minimum_business_rate_id = #{row.minimumBusinessRateId},
                maximum_rate = #{row.maximumRate},
                maximum_rate_source = #{row.maximumRateSource},
                maximum_business_rate_id = #{row.maximumBusinessRateId},
                latest_rate = #{row.latestRate},
                latest_rate_source = #{row.latestRateSource},
                latest_business_rate_id = #{row.latestBusinessRateId},
                volatility_rate = #{row.volatilityRate},
                recovery_streak = #{row.recoveryStreak},
                update_time = #{row.updateTime},
                version = version + 1
            WHERE id = #{row.id}
              AND version = #{row.version}
              AND active_event_key IS NOT NULL
              AND event_status IN ('OPEN', 'ACKNOWLEDGED')
            """)
    int updateRecoveryStreak(@Param("row") ExchangeRateAlertEventDO row);

    /** 连续低于恢复阈值后转为恢复终态并释放活动键。 */
    @Update("""
            UPDATE exchange_rate_alert_event
            SET active_event_key = NULL,
                window_start_time = #{row.windowStartTime},
                window_end_time = #{row.windowEndTime},
                sample_count = #{row.sampleCount},
                minimum_rate = #{row.minimumRate},
                minimum_rate_source = #{row.minimumRateSource},
                minimum_business_rate_id = #{row.minimumBusinessRateId},
                maximum_rate = #{row.maximumRate},
                maximum_rate_source = #{row.maximumRateSource},
                maximum_business_rate_id = #{row.maximumBusinessRateId},
                latest_rate = #{row.latestRate},
                latest_rate_source = #{row.latestRateSource},
                latest_business_rate_id = #{row.latestBusinessRateId},
                volatility_rate = #{row.volatilityRate},
                recovery_streak = #{row.recoveryStreak},
                event_status = 'RESOLVED',
                resolved_time = #{row.resolvedTime},
                update_time = #{row.updateTime},
                version = version + 1
            WHERE id = #{row.id}
              AND version = #{row.version}
              AND active_event_key IS NOT NULL
              AND event_status IN ('OPEN', 'ACKNOWLEDGED')
            """)
    int resolve(@Param("row") ExchangeRateAlertEventDO row);

    /** 人工确认仅允许 OPEN 到 ACKNOWLEDGED。 */
    @Update("""
            UPDATE exchange_rate_alert_event
            SET event_status = 'ACKNOWLEDGED',
                acknowledged_by = #{operator},
                acknowledged_time = #{now},
                acknowledge_remark = #{remark},
                update_time = #{now},
                version = version + 1
            WHERE id = #{id} AND version = #{version} AND deleted = 0 AND event_status = 'OPEN'
            """)
    int acknowledge(@Param("id") Long id, @Param("version") Integer version,
                    @Param("operator") String operator, @Param("remark") String remark,
                    @Param("now") LocalDateTime now);

    /** 人工关闭仅允许已自动恢复事件进入 CLOSED。 */
    @Update("""
            UPDATE exchange_rate_alert_event
            SET event_status = 'CLOSED',
                closed_by = #{operator},
                closed_time = #{now},
                close_remark = #{remark},
                update_time = #{now},
                version = version + 1
            WHERE id = #{id} AND version = #{version} AND deleted = 0 AND event_status = 'RESOLVED'
            """)
    int close(@Param("id") Long id, @Param("version") Integer version,
              @Param("operator") String operator, @Param("remark") String remark,
              @Param("now") LocalDateTime now);
}
