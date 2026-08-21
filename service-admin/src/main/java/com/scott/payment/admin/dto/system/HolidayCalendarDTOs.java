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
 * @author : scott
 * @version : v1.0.0
 * @classname : HolidayCalendarDTOs
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 中国大陆结算节假日日历查询、初始化、批量维护和确认模型。
 * @status : create
 */
public final class HolidayCalendarDTOs {

    private HolidayCalendarDTOs() {
    }

    /** 年月查询条件。 */
    @Data
    public static class CalendarMonthQuery {
        @Min(2000)
        @Max(2100)
        private int year;
        @Min(1)
        @Max(12)
        private int month;
    }

    /** 初始化指定自然年。 */
    @Data
    public static class CalendarYearInitializeRequest {
        @Min(2000)
        @Max(2100)
        private int year;
    }

    /** 单日批量维护输入。 */
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

    /** 批量维护或导入输入。 */
    @Data
    public static class CalendarBatchSaveRequest {
        @NotEmpty
        @Valid
        private List<CalendarDaySaveRequest> days = new ArrayList<>();
    }

    /** 年度状态摘要。 */
    @Data
    public static class CalendarYearResponse {
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
    }

    /** 单日配置和审计信息。 */
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
        private String updateBy;
        private LocalDateTime updateTime;
    }

    /** 月视图结果。 */
    @Data
    public static class CalendarMonthResponse {
        private CalendarYearResponse year;
        private List<CalendarDayResponse> days = new ArrayList<>();
    }
}
