package com.scott.payment.admin.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HolidayCalendarEntities
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 全局中国大陆结算节假日日历持久化模型。
 * @status : create
 */
public final class HolidayCalendarEntities {

    private HolidayCalendarEntities() {
    }

    /** 日历年度及确认状态。 */
    @Data
    @TableName("settlement_calendar_year")
    public static class CalendarYearDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Integer calendarYear;
        private String regionCode;
        private String timeZone;
        private String yearStatus;
        private Integer totalDays;
        private String confirmedBy;
        private LocalDateTime confirmedTime;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 自然日工作属性。 */
    @Data
    @TableName("settlement_holiday_calendar")
    public static class CalendarDayDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long calendarYearId;
        private LocalDate calendarDate;
        private Integer dayOfWeek;
        private String dayType;
        private String holidayName;
        private Integer statutoryHoliday;
        private Integer adjustedWorkday;
        private String dataSource;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }
}
