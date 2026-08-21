package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminHolidayCalendarApplicationService;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarMonthResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHolidayCalendarControllerTests
 * @date : 2026-08-18 20:15
 * @email : scott_x@163.com
 * @description : 节假日日历接口契约测试，验证年月查询参数在未保留 Java 参数名时仍能稳定绑定。
 * @status : create
 */
class AdminHolidayCalendarControllerTests {

    /** 查询接口必须按显式参数名绑定年月并调用应用服务。 */
    @Test
    void shouldBindYearAndMonthQueryParameters() throws Exception {
        AdminHolidayCalendarApplicationService applicationService = mock(AdminHolidayCalendarApplicationService.class);
        when(applicationService.getMonth(2026, 8)).thenReturn(new CalendarMonthResponse());
        MockMvc mockMvc = standaloneSetup(new AdminHolidayCalendarController(applicationService)).build();

        mockMvc.perform(get("/admin/system/holiday-calendar")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().isOk());

        verify(applicationService).getMonth(2026, 8);
    }

    /** 年度确认接口必须按显式参数名绑定自然年。 */
    @Test
    void shouldBindYearForCalendarConfirmation() throws Exception {
        AdminHolidayCalendarApplicationService applicationService = mock(AdminHolidayCalendarApplicationService.class);
        MockMvc mockMvc = standaloneSetup(new AdminHolidayCalendarController(applicationService)).build();

        mockMvc.perform(put("/admin/system/holiday-calendar/years/confirm")
                        .param("year", "2026"))
                .andExpect(status().isOk());

        verify(applicationService).confirmYear(2026);
    }

    /** 年度导出接口必须按显式参数名绑定自然年并传递下载响应。 */
    @Test
    void shouldBindYearForCalendarExport() throws Exception {
        AdminHolidayCalendarApplicationService applicationService = mock(AdminHolidayCalendarApplicationService.class);
        MockMvc mockMvc = standaloneSetup(new AdminHolidayCalendarController(applicationService)).build();

        mockMvc.perform(post("/admin/system/holiday-calendar/export")
                        .param("year", "2026"))
                .andExpect(status().isOk());

        verify(applicationService).exportYear(eq(2026), any(HttpServletResponse.class));
    }
}
