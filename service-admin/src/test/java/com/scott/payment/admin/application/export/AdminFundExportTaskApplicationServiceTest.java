package com.scott.payment.admin.application.export;

import com.scott.payment.admin.client.data.DataExportTaskInternalClient;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundAccountSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundDeductionSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundLedgerSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundRechargeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundAccountQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDetailQuery;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeQuery;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端资金账户域导出任务编排测试，验证查询、账户主键和可信商户范围冻结。
 */
class AdminFundExportTaskApplicationServiceTest {

    /** 清理线程认证和语言上下文。 */
    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    /** 四类资金任务必须冻结强类型条件，指定账户流水还必须冻结账户主键。 */
    @Test
    void shouldFreezeFundQueriesAndTrustedScope() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver scopeResolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        when(scopeResolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M8001")));
        when(client.submit(any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service =
                new AdminExportTaskApplicationService(client, scopeResolver);

        FundAccountQuery accountQuery = new FundAccountQuery();
        accountQuery.setSettlementCurrency("EUR");
        accountQuery.setPageNo(7);
        accountQuery.setPageSize(100);
        FundAccountSubmitRequest accountRequest = new FundAccountSubmitRequest();
        accountRequest.setRequestId("fund-account-1");
        accountRequest.setQuery(accountQuery);
        service.submitFundAccounts(accountRequest);

        FundDetailQuery ledgerQuery = new FundDetailQuery();
        ledgerQuery.setBusinessType("TRANSACTION_SETTLEMENT");
        ledgerQuery.setPageNo(8);
        ledgerQuery.setPageSize(50);
        FundLedgerSubmitRequest ledgerRequest = new FundLedgerSubmitRequest();
        ledgerRequest.setRequestId("fund-ledger-1");
        ledgerRequest.setAccountId(7001L);
        ledgerRequest.setQuery(ledgerQuery);
        service.submitFundLedgers(ledgerRequest);

        FundRechargeQuery rechargeQuery = new FundRechargeQuery();
        rechargeQuery.setRechargeStatus("PENDING_AUDIT");
        FundRechargeSubmitRequest rechargeRequest = new FundRechargeSubmitRequest();
        rechargeRequest.setRequestId("fund-recharge-1");
        rechargeRequest.setQuery(rechargeQuery);
        service.submitFundRecharges(rechargeRequest);

        FundDeductionQuery deductionQuery = new FundDeductionQuery();
        deductionQuery.setDeductionCategory("EXTRA_FEE");
        FundDeductionSubmitRequest deductionRequest = new FundDeductionSubmitRequest();
        deductionRequest.setRequestId("fund-deduction-1");
        deductionRequest.setQuery(deductionQuery);
        service.submitFundDeductions(deductionRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, times(4)).submit(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands).extracting(CreateRequest::getTaskType).containsExactly(
                "ADMIN_FUND_ACCOUNT", "ADMIN_FUND_LEDGER",
                "ADMIN_FUND_RECHARGE", "ADMIN_FUND_DEDUCTION");
        assertThat(commands).allSatisfy(command -> {
            assertThat(command.getCreatorAccountId()).isEqualTo(88L);
            assertThat(command.getMerchantIds()).containsExactly("M8001");
            assertThat(command.isAllMerchants()).isFalse();
        });
        assertThat(commands.get(0).getTaskName()).isEqualTo("资金账户导出");
        assertThat(commands.get(0).getFundAccountQuery().getSettlementCurrency()).isEqualTo("EUR");
        assertThat(commands.get(1).getFundLedgerQuery().getAccountId()).isEqualTo(7001L);
        assertThat(commands.get(1).getFundLedgerQuery().getBusinessType())
                .isEqualTo("TRANSACTION_SETTLEMENT");
        assertThat(commands.get(2).getFundRechargeQuery().getRechargeStatus())
                .isEqualTo("PENDING_AUDIT");
        assertThat(commands.get(3).getFundDeductionQuery().getDeductionCategory())
                .isEqualTo("EXTRA_FEE");
        assertThat(java.util.Arrays.stream(commands.get(1).getFundLedgerQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
    }

    /** 构造可信管理端操作人。 */
    private InternalAuthAccount adminAccount() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(1L);
        account.setAccountId(88L);
        account.setLoginAccount("fund.export@example.test");
        account.setRealName("Fund Export Operator");
        return account;
    }
}
