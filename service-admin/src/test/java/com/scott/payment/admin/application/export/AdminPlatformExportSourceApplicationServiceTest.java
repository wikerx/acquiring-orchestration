package com.scott.payment.admin.application.export;

import com.scott.payment.admin.application.base.AdminBaseCardBinApplicationService;
import com.scott.payment.admin.application.base.AdminBaseCountryApplicationService;
import com.scott.payment.admin.application.base.AdminBaseCurrencyApplicationService;
import com.scott.payment.admin.application.base.AdminBaseMccApplicationService;
import com.scott.payment.admin.application.base.AdminBaseRegionCurrencyApplicationService;
import com.scott.payment.admin.application.monitor.AdminJobSchedulerApplicationService;
import com.scott.payment.admin.dto.base.CardBinDTOs.CardBinQueryRequest;
import com.scott.payment.admin.dto.base.CardBinDTOs.CardBinResponse;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CardBinPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CardBinQuery;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CountryPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.CurrencyPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.JobRunLogPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.JobRunLogQuery;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.MccCodePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.RegionCurrencyPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SecurityInterceptEventPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminPlatformExportInternalDTOs.SecurityInterceptEventQuery;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventResponse;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.admin.service.AdminSecurityInterceptEventService;
import jakarta.validation.constraints.Max;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台级异步导出来源测试，验证内部分页覆盖和敏感字段裁剪。
 */
class AdminPlatformExportSourceApplicationServiceTest {

    /**
     * 平台级大数据导出内部页必须允许 500 行，减少全量导出的跨服务调用次数。
     */
    @Test
    void shouldAllowFiveHundredRowsForPlatformExportPages() throws Exception {
        Max cardBinLimit = CardBinPageRequest.class.getDeclaredField("pageSize").getAnnotation(Max.class);
        Max jobLogLimit = JobRunLogPageRequest.class.getDeclaredField("pageSize").getAnnotation(Max.class);
        Max countryLimit = CountryPageRequest.class.getDeclaredField("pageSize").getAnnotation(Max.class);
        Max currencyLimit = CurrencyPageRequest.class.getDeclaredField("pageSize").getAnnotation(Max.class);
        Max regionCurrencyLimit = RegionCurrencyPageRequest.class.getDeclaredField("pageSize").getAnnotation(Max.class);
        Max mccCodeLimit = MccCodePageRequest.class.getDeclaredField("pageSize").getAnnotation(Max.class);

        assertThat(cardBinLimit.value()).isEqualTo(500L);
        assertThat(jobLogLimit.value()).isEqualTo(500L);
        assertThat(countryLimit.value()).isEqualTo(500L);
        assertThat(currencyLimit.value()).isEqualTo(500L);
        assertThat(regionCurrencyLimit.value()).isEqualTo(500L);
        assertThat(mccCodeLimit.value()).isEqualTo(500L);
    }

    /**
     * 卡 BIN 来源必须复用现有分页查询，并输出字典展示值而非数据库内部标识。
     */
    @Test
    void shouldPageCardBinsWithInternalPagination() {
        AdminBaseCardBinApplicationService cardBinService = mock(AdminBaseCardBinApplicationService.class);
        AdminJobSchedulerApplicationService jobService = mock(AdminJobSchedulerApplicationService.class);
        CardBinResponse source = new CardBinResponse();
        source.setId(999L);
        source.setLegacyPkId(888L);
        source.setCardBinStart("400000");
        source.setCardBinEnd("499999");
        source.setCardBrandName("Visa");
        source.setCardTypeName("Credit");
        source.setDataSourceName("Manual");
        source.setStatusName("Enabled");
        when(cardBinService.page(any())).thenReturn(PageResult.of(1L, 4L, 100L, List.of(source)));
        AdminPlatformExportSourceApplicationService service = service(
                cardBinService, jobService, mock(AdminSecurityInterceptEventService.class));
        CardBinQuery query = new CardBinQuery();
        query.setCardBrand("VISA");
        CardBinPageRequest request = new CardBinPageRequest();
        request.setCreatorAccountId(88L);
        request.setQuery(query);
        request.setPageNo(4);
        request.setPageSize(100);

        var response = service.pageCardBins(request);

        ArgumentCaptor<CardBinQueryRequest> captor = ArgumentCaptor.forClass(CardBinQueryRequest.class);
        verify(cardBinService).page(captor.capture());
        assertThat(captor.getValue().getCardBrand()).isEqualTo("VISA");
        assertThat(captor.getValue().getPageNo()).isEqualTo(4);
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getCardBinStart()).isEqualTo("400000");
            assertThat(row.getCardBrand()).isEqualTo("Visa");
            assertThat(row.getCardType()).isEqualTo("Credit");
        });
        assertThat(response.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("id", "legacyPkId", "sourceBatchNo");
    }

    /**
     * 任务日志来源必须调用分页接口，并剔除参数快照、完整结果和追踪字段。
     */
    @Test
    void shouldPageJobRunLogsWithoutSensitivePayloads() {
        AdminBaseCardBinApplicationService cardBinService = mock(AdminBaseCardBinApplicationService.class);
        AdminJobSchedulerApplicationService jobService = mock(AdminJobSchedulerApplicationService.class);
        JobRunLogResponse source = new JobRunLogResponse();
        source.setRunId("RUN-1001");
        source.setJobCode("DATA_EXPORT_TASK_DISPATCH");
        source.setRunStatus("FAILED");
        source.setErrorMessage("upstream unavailable");
        source.setParamsSnapshot("{\"secret\":\"must-not-export\"}");
        source.setResultMessage("complete internal response");
        source.setTraceId("trace-sensitive");
        when(jobService.pageRunLogsForExport(any()))
                .thenReturn(PageResult.of(1L, 2L, 50L, List.of(source)));
        AdminPlatformExportSourceApplicationService service = service(
                cardBinService, jobService, mock(AdminSecurityInterceptEventService.class));
        JobRunLogQuery query = new JobRunLogQuery();
        query.setRunStatus("FAILED");
        JobRunLogPageRequest request = new JobRunLogPageRequest();
        request.setCreatorAccountId(88L);
        request.setQuery(query);
        request.setPageNo(2);
        request.setPageSize(50);

        var response = service.pageJobRunLogs(request);

        ArgumentCaptor<JobRunLogQueryRequest> captor = ArgumentCaptor.forClass(JobRunLogQueryRequest.class);
        verify(jobService).pageRunLogsForExport(captor.capture());
        assertThat(captor.getValue().getRunStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getRunId()).isEqualTo("RUN-1001");
            assertThat(row.getErrorMessage()).isEqualTo("upstream unavailable");
        });
        assertThat(response.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("paramsSnapshot", "resultMessage", "traceId", "operatorId", "operatorName");
    }

    /**
     * 安全事件来源必须使用冻结商户范围，并排除请求头、User-Agent 和请求号。
     */
    @Test
    void shouldPageSecurityEventsWithinFrozenScopeWithoutSensitivePayloads() {
        AdminBaseCardBinApplicationService cardBinService = mock(AdminBaseCardBinApplicationService.class);
        AdminJobSchedulerApplicationService jobService = mock(AdminJobSchedulerApplicationService.class);
        AdminSecurityInterceptEventService securityService =
                mock(AdminSecurityInterceptEventService.class);
        SecurityInterceptEventResponse source = new SecurityInterceptEventResponse();
        source.setEventNo("SEC-5001");
        source.setMerchantId("M5001");
        source.setRiskLevel("HIGH");
        source.setUserAgent("sensitive-agent");
        source.setHeaderSummary("{sensitive-header}");
        source.setRequestId("sensitive-request-id");
        when(securityService.pageEvents(any(), any()))
                .thenReturn(PageResult.of(1L, 2L, 100L, List.of(source)));
        AdminPlatformExportSourceApplicationService service = service(
                cardBinService, jobService, securityService);
        SecurityInterceptEventQuery query = new SecurityInterceptEventQuery();
        query.setRiskLevel("HIGH");
        SecurityInterceptEventPageRequest request = new SecurityInterceptEventPageRequest();
        request.setCreatorAccountId(88L);
        request.setMerchantIds(Set.of("M5001"));
        request.setQuery(query);
        request.setPageNo(2);
        request.setPageSize(100);

        var response = service.pageSecurityInterceptEvents(request);

        ArgumentCaptor<com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery>
                queryCaptor = ArgumentCaptor.forClass(
                com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery.class);
        ArgumentCaptor<com.scott.payment.admin.service.AdminMerchantDataScope> scopeCaptor =
                ArgumentCaptor.forClass(com.scott.payment.admin.service.AdminMerchantDataScope.class);
        verify(securityService).pageEvents(queryCaptor.capture(), scopeCaptor.capture());
        assertThat(queryCaptor.getValue().getRiskLevel()).isEqualTo("HIGH");
        assertThat(queryCaptor.getValue().getPageNo()).isEqualTo(2);
        assertThat(scopeCaptor.getValue().merchantIds()).containsExactly("M5001");
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getEventNo()).isEqualTo("SEC-5001");
            assertThat(row.getRiskLevel()).isEqualTo("HIGH");
        });
        assertThat(response.getRecords().get(0).getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("userAgent", "headerSummary", "requestId", "serviceName");
    }

    /**
     * 使用默认基础数据依赖创建被测服务，原有用例只关注既有平台导出来源。
     */
    private AdminPlatformExportSourceApplicationService service(
            AdminBaseCardBinApplicationService cardBinService,
            AdminJobSchedulerApplicationService jobService,
            AdminSecurityInterceptEventService securityService) {
        return new AdminPlatformExportSourceApplicationService(
                cardBinService,
                mock(AdminBaseCountryApplicationService.class),
                mock(AdminBaseCurrencyApplicationService.class),
                mock(AdminBaseRegionCurrencyApplicationService.class),
                mock(AdminBaseMccApplicationService.class),
                jobService,
                securityService,
                mock(com.scott.payment.admin.service.AdminDeptService.class),
                mock(com.scott.payment.admin.service.AdminPostService.class),
                mock(com.scott.payment.admin.service.AdminDictService.class),
                mock(com.scott.payment.admin.service.AdminHolidayCalendarService.class),
                mock(com.scott.payment.admin.application.system.AdminUserApplicationService.class),
                mock(com.scott.payment.admin.application.system.AdminConfigApplicationService.class),
                mock(com.scott.payment.admin.application.merchant.AdminMerchantIpWhitelistApplicationService.class),
                new com.scott.payment.admin.application.system.SystemConfigExportValuePolicy());
    }
}
