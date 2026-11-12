package com.scott.payment.admin.application.export;

import com.scott.payment.admin.application.exchange.AdminExchangeRateVolatilityApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityOverview;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.VolatilityPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.VolatilityQuery;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.BusinessRatePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RawRatePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RawRateQuery;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.UsageSnapshotPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.UsageSnapshotQuery;
import com.scott.payment.admin.service.AdminExchangeRateService;
import com.scott.payment.component.core.model.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端汇率事实导出源测试，验证服务端分页覆盖和十进制事实原值投影。
 */
class AdminExchangeExportSourceApplicationServiceTest {

    /** 原始汇率来源必须覆盖浏览器分页并保持所有报价字段精度。 */
    @Test
    void shouldPageRawRatesWithoutChangingDecimalFacts() {
        AdminExchangeRateService rateService = mock(AdminExchangeRateService.class);
        RawRateResponse source = new RawRateResponse();
        source.setSourceCode("BOC");
        source.setSpotBuyRate(new BigDecimal("0.00000001"));
        source.setMiddleRate(new BigDecimal("7.1234567800"));
        when(rateService.pageRawRates(any())).thenReturn(PageResult.of(1L, 3, 73, List.of(source)));
        AdminExchangeExportSourceApplicationService service =
                new AdminExchangeExportSourceApplicationService(
                        rateService, mock(AdminExchangeRateVolatilityApplicationService.class));
        RawRateQuery query = new RawRateQuery();
        query.setSourceCode("BOC");
        RawRatePageRequest request = new RawRatePageRequest();
        request.setCreatorAccountId(88L);
        request.setQuery(query);
        request.setPageNo(3);
        request.setPageSize(73);

        var response = service.pageRawRates(request);

        ArgumentCaptor<com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery> captor =
                ArgumentCaptor.forClass(
                        com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery.class);
        verify(rateService).pageRawRates(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(response.getRecords().get(0).getSpotBuyRate()).isEqualByComparingTo("0.00000001");
        assertThat(response.getRecords().get(0).getMiddleRate()).isEqualByComparingTo("7.1234567800");
    }

    /** 业务汇率和使用快照不得取倒数、换方向或舍入。 */
    @Test
    void shouldPreserveBusinessAndUsageRates() {
        AdminExchangeRateService rateService = mock(AdminExchangeRateService.class);
        BusinessRateResponse business = new BusinessRateResponse();
        business.setOriginalRate(new BigDecimal("1.234567890123"));
        business.setFinalRate(new BigDecimal("1.234500000001"));
        UsageSnapshotResponse snapshot = new UsageSnapshotResponse();
        snapshot.setUsedRate(new BigDecimal("0.712335440000"));
        snapshot.setSourceQuoteDirection("INVERSE");
        when(rateService.pageBusinessRates(any()))
                .thenReturn(PageResult.of(1L, 2, 61, List.of(business)));
        when(rateService.pageUsageSnapshots(any()))
                .thenReturn(PageResult.of(1L, 4, 67, List.of(snapshot)));
        AdminExchangeExportSourceApplicationService service =
                new AdminExchangeExportSourceApplicationService(
                        rateService, mock(AdminExchangeRateVolatilityApplicationService.class));
        BusinessRatePageRequest businessRequest = new BusinessRatePageRequest();
        businessRequest.setCreatorAccountId(88L);
        businessRequest.setQuery(new BusinessRateQuery());
        businessRequest.setPageNo(2);
        businessRequest.setPageSize(61);
        UsageSnapshotPageRequest snapshotRequest = new UsageSnapshotPageRequest();
        snapshotRequest.setCreatorAccountId(88L);
        snapshotRequest.setQuery(new UsageSnapshotQuery());
        snapshotRequest.setPageNo(4);
        snapshotRequest.setPageSize(67);

        var businessPage = service.pageBusinessRates(businessRequest);
        var snapshotPage = service.pageUsageSnapshots(snapshotRequest);

        assertThat(businessPage.getRecords().get(0).getOriginalRate())
                .isEqualByComparingTo("1.234567890123");
        assertThat(businessPage.getRecords().get(0).getFinalRate())
                .isEqualByComparingTo("1.234500000001");
        assertThat(snapshotPage.getRecords().get(0).getUsedRate())
                .isEqualByComparingTo("0.712335440000");
        assertThat(snapshotPage.getRecords().get(0).getSourceQuoteDirection()).isEqualTo("INVERSE");
    }

    /** 波动导出必须复用权威算法结果并保持十进制字符串，不允许 Data 侧重算。 */
    @Test
    void shouldPageAuthorityVolatilityWithoutRecalculation() {
        AdminExchangeRateService rateService = mock(AdminExchangeRateService.class);
        AdminExchangeRateVolatilityApplicationService volatilityService =
                mock(AdminExchangeRateVolatilityApplicationService.class);
        VolatilityOverview overview = new VolatilityOverview();
        overview.setRateType("TRANSACTION_RATE");
        overview.setBaseCurrency("EUR");
        overview.setQuoteCurrency("USD");
        overview.setLatestRate("1.10000000");
        overview.setVolatilityRate("3.00000000");
        when(volatilityService.overview(any())).thenReturn(List.of(overview));
        AdminExchangeExportSourceApplicationService service =
                new AdminExchangeExportSourceApplicationService(rateService, volatilityService);
        VolatilityQuery query = new VolatilityQuery();
        query.setRateType("TRANSACTION_RATE");
        VolatilityPageRequest request = new VolatilityPageRequest();
        request.setCreatorAccountId(88L);
        request.setQuery(query);
        request.setPageNo(1);
        request.setPageSize(73);

        var response = service.pageVolatility(request);

        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRecords().get(0).getLatestRate()).isEqualTo("1.10000000");
        assertThat(response.getRecords().get(0).getVolatilityRate()).isEqualTo("3.00000000");
        verify(volatilityService).overview(any());
    }
}
