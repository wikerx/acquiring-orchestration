package com.scott.payment.admin.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 全局中国大陆结算节假日日历持久化模型。
 */
public final class HolidayCalendarEntities {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private HolidayCalendarEntities() {
    }

    /**
     * 日历年度及确认状态。
     */
    @Data
    @TableName("settlement_calendar_year")
    public static class CalendarYearDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 持久化的{@code calendarYear}，用于还原当前记录的业务事实。
         */
        private Integer calendarYear;
        private String regionCode;
        /**
         * 时间时区，使用 IANA 时区标识解释关联的本地日期时间。
         */
        private String timeZone;
        private String yearStatus;
        private Integer totalDays;
        /**
         * 持久化的{@code confirmedBy}，用于还原当前记录的业务事实。
         */
        private String confirmedBy;
        /**
         * 持久化的{@code confirmedTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime confirmedTime;
        /**
         * 记录创建人账号标识，用于操作审计。
         */
        private String createBy;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }

    /**
     * 自然日工作属性。
     */
    @Data
    @TableName("settlement_holiday_calendar")
    public static class CalendarDayDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long calendarYearId;
        /**
         * 持久化的{@code calendarDate}，用于还原当前记录的业务事实。
         */
        private LocalDate calendarDate;
        /**
         * 持久化的{@code dayOfWeek}，用于还原当前记录的业务事实。
         */
        private Integer dayOfWeek;
        private String dayType;
        private String holidayName;
        /**
         * 持久化的{@code statutoryHoliday}，用于还原当前记录的业务事实。
         */
        private Integer statutoryHoliday;
        /**
         * 持久化的{@code adjustedWorkday}，用于还原当前记录的业务事实。
         */
        private Integer adjustedWorkday;
        /**
         * 持久化的{@code dataSource}，用于还原当前记录的业务事实。
         */
        private String dataSource;
        private String remark;
        /**
         * 记录创建人账号标识，用于操作审计。
         */
        private String createBy;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }
}
