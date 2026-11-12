package com.scott.payment.admin.application.export;

import com.scott.payment.admin.application.base.AdminBaseCardBinApplicationService;
import com.scott.payment.admin.application.base.AdminBaseCountryApplicationService;
import com.scott.payment.admin.application.base.AdminBaseCurrencyApplicationService;
import com.scott.payment.admin.application.base.AdminBaseMccApplicationService;
import com.scott.payment.admin.application.base.AdminBaseRegionCurrencyApplicationService;
import com.scott.payment.admin.application.monitor.AdminJobSchedulerApplicationService;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DepartmentPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DepartmentQuery;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictDataPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictDataQuery;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictTypePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.DictTypeQuery;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.HolidayCalendarPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.HolidayCalendarQuery;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.PostPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.PostQuery;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemConfigPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SystemConfigQuery;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarDayResponse;
import com.scott.payment.admin.service.AdminDeptService;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.admin.service.AdminHolidayCalendarService;
import com.scott.payment.admin.service.AdminPostService;
import com.scott.payment.admin.service.AdminSecurityInterceptEventService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端组织、字典与日历导出来源测试，验证真实分页和最小安全投影。
 */
class AdminSystemExportSourceApplicationServiceTest {

    /**
     * 部门与岗位导出必须使用服务端分页，并保持层级名称和冻结筛选条件。
     */
    @Test
    void shouldPageOrganizationSourcesWithFrozenFilters() {
        AdminDeptService deptService = mock(AdminDeptService.class);
        AdminPostService postService = mock(AdminPostService.class);
        AdminDictService dictService = mock(AdminDictService.class);
        AdminHolidayCalendarService calendarService = mock(AdminHolidayCalendarService.class);
        SysDeptDO parent = new SysDeptDO();
        parent.setId(10L);
        parent.setDeptName("Operations");
        SysDeptDO child = new SysDeptDO();
        child.setId(11L);
        child.setParentId(10L);
        child.setDeptName("Payment Operations");
        child.setPhone("0012345678");
        when(deptService.pageDepts(3, 100))
                .thenReturn(PageResult.of(1L, 3L, 100L, List.of(child)));
        when(deptService.exportDepts()).thenReturn(List.of(parent, child));
        SysPostDO post = new SysPostDO();
        post.setPostCode("0010");
        post.setPostName("Payment Operator");
        post.setStatus(1);
        when(postService.pagePosts(2, 50, "0010", "Payment", 1))
                .thenReturn(PageResult.of(1L, 2L, 50L, List.of(post)));
        AdminPlatformExportSourceApplicationService service = service(
                deptService, postService, dictService, calendarService);

        DepartmentPageRequest departmentRequest = new DepartmentPageRequest();
        departmentRequest.setCreatorAccountId(88L);
        departmentRequest.setQuery(new DepartmentQuery());
        departmentRequest.setPageNo(3);
        departmentRequest.setPageSize(100);
        var departments = service.pageDepartments(departmentRequest);

        PostQuery postQuery = new PostQuery();
        postQuery.setPostCode("0010");
        postQuery.setPostName("Payment");
        postQuery.setStatus(1);
        PostPageRequest postRequest = new PostPageRequest();
        postRequest.setCreatorAccountId(88L);
        postRequest.setQuery(postQuery);
        postRequest.setPageNo(2);
        postRequest.setPageSize(50);
        var posts = service.pagePosts(postRequest);

        assertThat(departments.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getDeptName()).isEqualTo("Payment Operations");
            assertThat(row.getParentName()).isEqualTo("Operations");
            assertThat(row.getPhone()).isEqualTo("0012345678");
        });
        assertThat(posts.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getPostCode()).isEqualTo("0010");
            assertThat(row.getPostName()).isEqualTo("Payment Operator");
        });
        assertThat(posts.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("id", "appId", "deleted");
    }

    /** 系统参数导出源必须在跨服务前脱敏隐藏、加密和敏感键值。 */
    @Test
    void shouldRedactSystemConfigValuesBeforeLeavingAdmin() {
        var configApplication = mock(
                com.scott.payment.admin.application.system.AdminConfigApplicationService.class);
        var plain = config("platform.display.name", "Acquiring", 1, 0);
        var encrypted = config("platform.mail.value", "cipher-text", 1, 1);
        var hidden = config("platform.internal.value", "hidden-value", 0, 0);
        var sensitive = config("security.jwt.secret", "raw-secret", 1, 0);
        when(configApplication.pageConfigs(any())).thenReturn(
                PageResult.of(4L, 2L, 50L, List.of(plain, encrypted, hidden, sensitive)));
        AdminPlatformExportSourceApplicationService service = new AdminPlatformExportSourceApplicationService(
                mock(AdminBaseCardBinApplicationService.class),
                mock(AdminBaseCountryApplicationService.class),
                mock(AdminBaseCurrencyApplicationService.class),
                mock(AdminBaseRegionCurrencyApplicationService.class),
                mock(AdminBaseMccApplicationService.class),
                mock(AdminJobSchedulerApplicationService.class),
                mock(AdminSecurityInterceptEventService.class),
                mock(AdminDeptService.class), mock(AdminPostService.class),
                mock(AdminDictService.class), mock(AdminHolidayCalendarService.class),
                mock(com.scott.payment.admin.application.system.AdminUserApplicationService.class),
                configApplication,
                mock(com.scott.payment.admin.application.merchant.AdminMerchantIpWhitelistApplicationService.class),
                new com.scott.payment.admin.application.system.SystemConfigExportValuePolicy());

        SystemConfigQuery query = new SystemConfigQuery();
        query.setConfigGroup("security");
        SystemConfigPageRequest request = new SystemConfigPageRequest();
        request.setQuery(query);
        request.setPageNo(2);
        request.setPageSize(50);
        var response = service.pageSystemConfigs(request);

        ArgumentCaptor<com.scott.payment.admin.dto.SysConfigQueryRequest> captor =
                ArgumentCaptor.forClass(com.scott.payment.admin.dto.SysConfigQueryRequest.class);
        verify(configApplication).pageConfigs(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(response.getRecords()).extracting(row -> row.getConfigValue())
                .containsExactly("Acquiring", "[REDACTED]", "[REDACTED]", "[REDACTED]");
    }

    private com.scott.payment.admin.dto.SysConfigDTO config(
            String key, String value, Integer visible, Integer encrypted) {
        var config = new com.scott.payment.admin.dto.SysConfigDTO();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setVisible(visible);
        config.setEncrypted(encrypted);
        return config;
    }

    /**
     * 字典与日历导出必须把冻结条件恢复到权威服务，并排除内部主键和扩展配置。
     */
    @Test
    void shouldPageDictionaryAndCalendarSourcesWithoutInternalFields() {
        AdminDeptService deptService = mock(AdminDeptService.class);
        AdminPostService postService = mock(AdminPostService.class);
        AdminDictService dictService = mock(AdminDictService.class);
        AdminHolidayCalendarService calendarService = mock(AdminHolidayCalendarService.class);
        SysDictTypeDTO type = new SysDictTypeDTO();
        type.setId(901L);
        type.setDictType("payment_status");
        type.setBizDomain("PAYMENT");
        SysDictDataDTO data = new SysDictDataDTO();
        data.setId(902L);
        data.setDictType("payment_status");
        data.setDictValue("0001");
        data.setExtraJson("{\"internal\":true}");
        CalendarDayResponse day = new CalendarDayResponse();
        day.setCalendarDate(LocalDate.of(2027, 1, 1));
        day.setDayType("HOLIDAY");
        when(dictService.pageDictTypes(any()))
                .thenReturn(PageResult.of(1L, 4L, 200L, List.of(type)));
        when(dictService.pageDictData(any()))
                .thenReturn(PageResult.of(1L, 5L, 200L, List.of(data)));
        when(calendarService.pageYearDays(2027, 6, 200))
                .thenReturn(PageResult.of(1L, 6L, 200L, List.of(day)));
        AdminPlatformExportSourceApplicationService service = service(
                deptService, postService, dictService, calendarService);

        DictTypeQuery typeQuery = new DictTypeQuery();
        typeQuery.setBizDomain("PAYMENT");
        DictTypePageRequest typeRequest = new DictTypePageRequest();
        typeRequest.setCreatorAccountId(88L);
        typeRequest.setQuery(typeQuery);
        typeRequest.setPageNo(4);
        typeRequest.setPageSize(200);
        var types = service.pageDictTypes(typeRequest);

        DictDataQuery dataQuery = new DictDataQuery();
        dataQuery.setDictType("payment_status");
        dataQuery.setDictValue("0001");
        dataQuery.setLocale("zh-CN");
        DictDataPageRequest dataRequest = new DictDataPageRequest();
        dataRequest.setCreatorAccountId(88L);
        dataRequest.setQuery(dataQuery);
        dataRequest.setPageNo(5);
        dataRequest.setPageSize(200);
        var values = service.pageDictData(dataRequest);

        HolidayCalendarQuery calendarQuery = new HolidayCalendarQuery();
        calendarQuery.setYear(2027);
        HolidayCalendarPageRequest calendarRequest = new HolidayCalendarPageRequest();
        calendarRequest.setCreatorAccountId(88L);
        calendarRequest.setQuery(calendarQuery);
        calendarRequest.setPageNo(6);
        calendarRequest.setPageSize(200);
        var calendar = service.pageHolidayCalendar(calendarRequest);

        ArgumentCaptor<SysDictTypeQueryRequest> typeCaptor =
                ArgumentCaptor.forClass(SysDictTypeQueryRequest.class);
        ArgumentCaptor<SysDictDataQueryRequest> dataCaptor =
                ArgumentCaptor.forClass(SysDictDataQueryRequest.class);
        verify(dictService).pageDictTypes(typeCaptor.capture());
        verify(dictService).pageDictData(dataCaptor.capture());
        assertThat(typeCaptor.getValue().getBizDomain()).isEqualTo("PAYMENT");
        assertThat(typeCaptor.getValue().getPageNo()).isEqualTo(4);
        assertThat(dataCaptor.getValue().getDictValue()).isEqualTo("0001");
        assertThat(dataCaptor.getValue().getPageNo()).isEqualTo(5);
        assertThat(types.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("id", "editable", "updatedAt");
        assertThat(values.getRecords()).singleElement().satisfies(row ->
                assertThat(row.getDictValue()).isEqualTo("0001"));
        assertThat(values.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("id", "extraJson", "parentValue");
        assertThat(calendar.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getCalendarDate()).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(row.getDayType()).isEqualTo("HOLIDAY");
        });
    }

    /**
     * 创建只注入本测试所需权威服务的平台导出来源实例。
     */
    private AdminPlatformExportSourceApplicationService service(
            AdminDeptService deptService,
            AdminPostService postService,
            AdminDictService dictService,
            AdminHolidayCalendarService calendarService) {
        return new AdminPlatformExportSourceApplicationService(
                mock(AdminBaseCardBinApplicationService.class),
                mock(AdminBaseCountryApplicationService.class),
                mock(AdminBaseCurrencyApplicationService.class),
                mock(AdminBaseRegionCurrencyApplicationService.class),
                mock(AdminBaseMccApplicationService.class),
                mock(AdminJobSchedulerApplicationService.class),
                mock(AdminSecurityInterceptEventService.class),
                deptService,
                postService,
                dictService,
                calendarService,
                mock(com.scott.payment.admin.application.system.AdminUserApplicationService.class),
                mock(com.scott.payment.admin.application.system.AdminConfigApplicationService.class),
                mock(com.scott.payment.admin.application.merchant.AdminMerchantIpWhitelistApplicationService.class),
                new com.scott.payment.admin.application.system.SystemConfigExportValuePolicy());
    }
}
