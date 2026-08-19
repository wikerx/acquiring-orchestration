package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.export.HolidayCalendarExportRow;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarBatchSaveRequest;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarDayResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarMonthResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarYearResponse;
import com.scott.payment.admin.service.AdminHolidayCalendarService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHolidayCalendarApplicationService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端结算节假日日历应用编排与年度导出服务。
 * @status : create
 */
@Service
public class AdminHolidayCalendarApplicationService {

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminHolidayCalendarService calendarService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver messageResolver;
    private final ExcelLocaleResolver localeResolver;

    /** 构造管理端结算节假日日历应用服务。 */
    public AdminHolidayCalendarApplicationService(AdminHolidayCalendarService calendarService,
                                                  ExcelExportService excelExportService,
                                                  ExcelI18nMessageResolver messageResolver,
                                                  ExcelLocaleResolver localeResolver) {
        this.calendarService = calendarService;
        this.excelExportService = excelExportService;
        this.messageResolver = messageResolver;
        this.localeResolver = localeResolver;
    }

    /** 查询月视图。 */
    public CalendarMonthResponse getMonth(int year, int month) {
        return calendarService.getMonth(year, month);
    }

    /** 初始化年度基础日历。 */
    public CalendarYearResponse initializeYear(int year) {
        return calendarService.initializeYear(year, currentOperatorName());
    }

    /** 批量保存或导入日历。 */
    public CalendarMonthResponse saveDays(CalendarBatchSaveRequest request) {
        return calendarService.saveDays(request, currentOperatorName());
    }

    /** 确认年度日历。 */
    public CalendarYearResponse confirmYear(int year) {
        return calendarService.confirmYear(year, currentOperatorName());
    }

    /** 导出指定年度全部日历日期。 */
    public void exportYear(int year, HttpServletResponse response) {
        List<HolidayCalendarExportRow> rows = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(month -> calendarService.getMonth(year, month).getDays())
                .flatMap(List::stream)
                .map(this::toExportRow)
                .toList();
        Locale locale = localeResolver.resolveCurrentLocale();
        String titleKey = "excel.calendar.title";
        String title = messageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(ExcelPagedExportRequest.<HolidayCalendarExportRow>builder()
                .fileName(title + "_" + year + "_" + EXPORT_TIME_FORMATTER.format(now))
                .sheetName(title)
                .titleKey(titleKey)
                .operator(currentOperatorName())
                .exportTime(now)
                .locale(locale)
                .querySummary("calendarYear=" + year + ", region=CN_MAINLAND, timeZone=Asia/Shanghai")
                .rowClass(HolidayCalendarExportRow.class)
                .pageSize(500)
                .pageLoader(pageNo -> pageNo == 1 ? rows : List.of())
                .build(), response);
    }

    private HolidayCalendarExportRow toExportRow(CalendarDayResponse source) {
        HolidayCalendarExportRow row = new HolidayCalendarExportRow();
        row.setCalendarDate(source.getCalendarDate());
        row.setDayOfWeek(source.getDayOfWeek());
        row.setDayType(source.getDayType());
        row.setHolidayName(source.getHolidayName());
        row.setStatutoryHoliday(source.getStatutoryHoliday());
        row.setAdjustedWorkday(source.getAdjustedWorkday());
        row.setRemark(source.getRemark());
        row.setUpdateBy(source.getUpdateBy());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null
                || !StringUtils.hasText(account.getLoginAccount())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "登录账号上下文缺失");
        }
        return StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
    }
}
