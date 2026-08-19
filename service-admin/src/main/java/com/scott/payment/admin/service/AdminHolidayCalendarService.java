package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarBatchSaveRequest;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarMonthResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarYearResponse;

import java.time.LocalDate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHolidayCalendarService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 全局中国大陆结算节假日日历服务契约。
 * @status : create
 */
public interface AdminHolidayCalendarService {
    /** 查询指定年月的日历及年度状态。 */
    CalendarMonthResponse getMonth(int year, int month);
    /** 初始化年度基础工作日日历。 */
    CalendarYearResponse initializeYear(int year, String operatorName);
    /** 批量维护法定节假日、普通休息日和调休工作日。 */
    CalendarMonthResponse saveDays(CalendarBatchSaveRequest request, String operatorName);
    /** 确认年度日历，确认后才允许 T+N 使用。 */
    CalendarYearResponse confirmYear(int year, String operatorName);
    /** 读取已确认日历中的工作日属性，缺失或草稿状态时阻断。 */
    boolean isWorkingDay(LocalDate date);
}
