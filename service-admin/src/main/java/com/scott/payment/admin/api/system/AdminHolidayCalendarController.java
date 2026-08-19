package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminHolidayCalendarApplicationService;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarBatchSaveRequest;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarMonthResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarYearInitializeRequest;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarYearResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHolidayCalendarController
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 系统管理下的中国大陆结算节假日日历接口。
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/holiday-calendar")
public class AdminHolidayCalendarController {

    private final AdminHolidayCalendarApplicationService applicationService;

    /** 构造结算节假日日历接口。 */
    public AdminHolidayCalendarController(AdminHolidayCalendarApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询指定年月。 */
    @GetMapping
    @RequiresPermission("system:calendar:list")
    public CommonResult<CalendarMonthResponse> getMonth(@RequestParam("year") int year,
                                                        @RequestParam("month") int month) {
        return success(applicationService.getMonth(year, month));
    }

    /** 初始化年度基础日历。 */
    @PostMapping("/years")
    @RequiresPermission("system:calendar:initialize")
    @OperationLog(moduleName = "节假日日历", businessType = OperationTypeConstants.CREATE, operation = "初始化年度日历")
    public CommonResult<CalendarYearResponse> initializeYear(
            @Valid @RequestBody CalendarYearInitializeRequest request) {
        return success(applicationService.initializeYear(request.getYear()));
    }

    /** 批量维护或导入日历日期。 */
    @PutMapping("/days")
    @RequiresPermission("system:calendar:edit")
    @OperationLog(moduleName = "节假日日历", businessType = OperationTypeConstants.UPDATE, operation = "批量维护日历")
    public CommonResult<CalendarMonthResponse> saveDays(
            @Valid @RequestBody CalendarBatchSaveRequest request) {
        return success(applicationService.saveDays(request));
    }

    /** 确认年度日历。 */
    @PutMapping("/years/confirm")
    @RequiresPermission("system:calendar:confirm")
    @OperationLog(moduleName = "节假日日历", businessType = OperationTypeConstants.AUDIT, operation = "确认年度日历")
    public CommonResult<CalendarYearResponse> confirmYear(@RequestParam("year") int year) {
        return success(applicationService.confirmYear(year));
    }

    /** 导出指定年度完整日历。 */
    @PostMapping("/export")
    @RequiresPermission("system:calendar:export")
    @OperationLog(moduleName = "节假日日历", businessType = OperationTypeConstants.EXPORT, operation = "导出年度日历")
    public void exportYear(@RequestParam("year") int year, HttpServletResponse response) {
        applicationService.exportYear(year, response);
    }
}
