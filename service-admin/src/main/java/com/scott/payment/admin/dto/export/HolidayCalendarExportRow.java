package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HolidayCalendarExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 中国大陆结算节假日日历导出行。
 * @status : create
 */
@Data
public class HolidayCalendarExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.calendar.date", width = 16)
    private LocalDate calendarDate;
    @ExcelExportColumn(order = 2, headerKey = "excel.calendar.dayOfWeek", width = 14)
    private Integer dayOfWeek;
    @ExcelExportColumn(order = 3, headerKey = "excel.calendar.dayType", width = 16)
    private String dayType;
    @ExcelExportColumn(order = 4, headerKey = "excel.calendar.holidayName", width = 24)
    private String holidayName;
    @ExcelExportColumn(order = 5, headerKey = "excel.calendar.statutoryHoliday", width = 18)
    private Boolean statutoryHoliday;
    @ExcelExportColumn(order = 6, headerKey = "excel.calendar.adjustedWorkday", width = 18)
    private Boolean adjustedWorkday;
    @ExcelExportColumn(order = 7, headerKey = "excel.calendar.remark", width = 32)
    private String remark;
    @ExcelExportColumn(order = 8, headerKey = "excel.calendar.updateBy", width = 18)
    private String updateBy;
    @ExcelExportColumn(order = 9, headerKey = "excel.calendar.updateTime", width = 22)
    private LocalDateTime updateTime;
}
