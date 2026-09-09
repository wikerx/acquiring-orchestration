package com.scott.payment.clearing.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/** 交易结算周期计算所需的已入账事实和已确认工作日日历查询。 */
public interface ClearingSettlementEligibilityMapper {

    /** 首个正式交易结算批次入账后，后续交易使用常规周期。 */
    @Select("""
            SELECT COUNT(1)
            FROM settlement_batch
            WHERE merchant_id = #{merchantId}
              AND batch_type = 'REGULAR'
              AND batch_status = 'POSTED'
            LIMIT 1
            """)
    int countPostedRegularSettlement(@Param("merchantId") String merchantId);

    /** 从已确认日历中读取起始日之后第 N 个工作日。 */
    @Select("""
            SELECT day.calendar_date
            FROM settlement_holiday_calendar day
            INNER JOIN settlement_calendar_year calendar_year
                    ON calendar_year.id = day.calendar_year_id
                   AND calendar_year.year_status = 'ACTIVE'
                   AND calendar_year.deleted = 0
            WHERE day.calendar_date > #{startDate}
              AND day.day_type = 'WORKDAY'
              AND day.deleted = 0
            ORDER BY day.calendar_date ASC
            LIMIT #{offset}, 1
            """)
    LocalDate selectNthConfirmedWorkday(@Param("startDate") LocalDate startDate,
                                        @Param("offset") int offset);

    /** 校验起始日之后至目标日的每个自然日都存在于已确认日历中。 */
    @Select("""
            SELECT COUNT(1)
            FROM settlement_holiday_calendar day
            INNER JOIN settlement_calendar_year calendar_year
                    ON calendar_year.id = day.calendar_year_id
                   AND calendar_year.year_status = 'ACTIVE'
                   AND calendar_year.deleted = 0
            WHERE day.calendar_date > #{startDate}
              AND day.calendar_date <= #{endDate}
              AND day.deleted = 0
            """)
    int countConfirmedCalendarDays(@Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);
}
