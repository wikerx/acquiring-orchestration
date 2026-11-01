package com.scott.payment.admin.dto.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 中国大陆结算节假日日历查询、初始化、批量维护和确认模型。
 */
public final class HolidayCalendarDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private HolidayCalendarDTOs() {
    }

    /**
     * 年月查询条件。
     */
    @Data
    public static class CalendarMonthQuery {
        /**
         * 请求中的年份，用于限定本次操作的输入和校验范围。
         */
        @Min(2000)
        @Max(2100)
        private int year;
        @Min(1)
        @Max(12)
        private int month;
    }

    /**
     * 初始化指定自然年。
     */
    @Data
    public static class CalendarYearInitializeRequest {
        /**
         * 请求中的年份，用于限定本次操作的输入和校验范围。
         */
        @Min(2000)
        @Max(2100)
        private int year;
    }

    /**
     * 单日批量维护输入。
     */
    @Data
    public static class CalendarDaySaveRequest {
        @NotNull
        private LocalDate calendarDate;
        @NotBlank
        private String dayType;
        @Size(max = 128)
        private String holidayName;
        private Boolean statutoryHoliday = false;
        private Boolean adjustedWorkday = false;
        @Size(max = 500)
        private String remark;
    }

    /**
     * 批量维护或导入输入。
     */
    @Data
    public static class CalendarBatchSaveRequest {
        @NotEmpty
        @Valid
        private List<CalendarDaySaveRequest> days = new ArrayList<>();
    }

    /**
     * 年度状态摘要。
     */
    @Data
    public static class CalendarYearResponse {
        private Long id;
        private Integer calendarYear;
        private String regionCode;
        /**
         * 时间时区，使用 IANA 时区标识解释关联的本地日期时间。
         */
        private String timeZone;
        private String yearStatus;
        private Integer totalDays;
        private String confirmedBy;
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
    }

    /**
     * 单日配置和审计信息。
     */
    @Data
    public static class CalendarDayResponse {
        private Long id;
        private LocalDate calendarDate;
        private Integer dayOfWeek;
        private String dayType;
        private String holidayName;
        private Boolean statutoryHoliday;
        private Boolean adjustedWorkday;
        private String dataSource;
        private String remark;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 月视图结果。
     */
    @Data
    public static class CalendarMonthResponse {
        private CalendarYearResponse year;
        private List<CalendarDayResponse> days = new ArrayList<>();
    }
}
