package com.scott.payment.admin.application.export;

import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundAccountPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundLedgerPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFundExportInternalDTOs.FundLedgerQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountResponse;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundLedgerResponse;
import com.scott.payment.admin.service.AdminFundAccountService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.core.model.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端资金导出源测试，验证服务端分页覆盖、冻结范围和资金事实投影。
 */
class AdminFundExportSourceApplicationServiceTest {

    /** 资金账户来源必须覆盖浏览器分页并把有限商户范围传给权威查询服务。 */
    @Test
    void shouldOverrideAccountPaginationAndPropagateFiniteScope() {
        AdminFundAccountService fundService = mock(AdminFundAccountService.class);
        FundAccountResponse source = new FundAccountResponse();
        source.setAccountNo("FA-1001");
        source.setMerchantId("M1001");
        source.setSettlementCurrency("EUR");
        source.setAvailableBalance(new BigDecimal("123.4500"));
        AdminMerchantDataScope scope = AdminMerchantDataScope.limited(Set.of("M1001"));
        when(fundService.pageAccountsForExport(org.mockito.ArgumentMatchers.any(), eq(scope)))
                .thenReturn(PageResult.of(1L, 3, 73, List.of(source)));
        AdminFundExportSourceApplicationService service =
                new AdminFundExportSourceApplicationService(fundService);
        FundAccountQuery query = new FundAccountQuery();
        query.setSettlementCurrency("EUR");
        FundAccountPageRequest request = new FundAccountPageRequest();
        request.setCreatorAccountId(88L);
        request.setAllMerchants(false);
        request.setMerchantIds(Set.of("M1001"));
        request.setQuery(query);
        request.setPageNo(3);
        request.setPageSize(73);

        var response = service.pageAccounts(request);

        ArgumentCaptor<com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery> captor =
                ArgumentCaptor.forClass(
                        com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery.class);
        verify(fundService).pageAccountsForExport(captor.capture(), eq(scope));
        assertThat(captor.getValue().getPageNo()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(response.getRecords().get(0).getAvailableBalance())
                .isEqualByComparingTo("123.4500");
        assertThat(response.getRecords().get(0).getSettlementCurrency()).isEqualTo("EUR");
    }

    /** 指定账户流水必须走账户级查询，不能退化为全局查询，并保持金额与币种不变。 */
    @Test
    void shouldUseAccountLedgerQueryAndPreserveMoneyFacts() {
        AdminFundAccountService fundService = mock(AdminFundAccountService.class);
        FundLedgerResponse source = new FundLedgerResponse();
        source.setLedgerNo("LEDGER-1001");
        source.setAmount(new BigDecimal("88.16000000"));
        source.setCurrency("USD");
        source.setBalanceBefore(new BigDecimal("100.00000000"));
        source.setBalanceAfter(new BigDecimal("188.16000000"));
        AdminMerchantDataScope scope = AdminMerchantDataScope.limited(Set.of("M1001"));
        when(fundService.pageLedgersForExport(eq(7001L),
                org.mockito.ArgumentMatchers.any(), eq(scope)))
                .thenReturn(PageResult.of(1L, 4, 73, List.of(source)));
        AdminFundExportSourceApplicationService service =
                new AdminFundExportSourceApplicationService(fundService);
        FundLedgerQuery query = new FundLedgerQuery();
        query.setAccountId(7001L);
        query.setBusinessType("TRANSACTION_SETTLEMENT");
        FundLedgerPageRequest request = new FundLedgerPageRequest();
        request.setCreatorAccountId(88L);
        request.setAllMerchants(false);
        request.setMerchantIds(Set.of("M1001"));
        request.setQuery(query);
        request.setPageNo(4);
        request.setPageSize(73);

        var response = service.pageLedgers(request);

        ArgumentCaptor<FundDetailQuery> queryCaptor = ArgumentCaptor.forClass(FundDetailQuery.class);
        verify(fundService).pageLedgersForExport(eq(7001L), queryCaptor.capture(), eq(scope));
        verify(fundService, never()).pageAllLedgersForExport(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(queryCaptor.getValue().getPageNo()).isEqualTo(4);
        assertThat(queryCaptor.getValue().getPageSize()).isEqualTo(73);
        assertThat(response.getRecords().get(0).getAmount()).isEqualByComparingTo("88.16000000");
        assertThat(response.getRecords().get(0).getBalanceAfter()).isEqualByComparingTo("188.16000000");
        assertThat(response.getRecords().get(0).getCurrency()).isEqualTo("USD");
    }
}
