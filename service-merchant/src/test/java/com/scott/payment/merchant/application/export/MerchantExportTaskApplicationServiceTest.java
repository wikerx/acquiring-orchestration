package com.scott.payment.merchant.application.export;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.merchant.client.data.MerchantDataExportTaskClient;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementTransactionSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.FundLedgerSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TaskSummary;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TransactionSubmitRequest;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundAccountResponse;
import com.scott.payment.merchant.service.MerchantFinanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户异步导出应用服务测试，验证账号、商户和查询范围只能来自可信登录上下文。
 */
class MerchantExportTaskApplicationServiceTest {

    /**
     * 每个用例结束后清理线程认证上下文，避免商户身份泄漏到后续测试。
     */
    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    /**
     * 交易导出必须覆盖浏览器查询中的商户号，并把当前账号身份冻结到 Data 命令。
     */
    @Test
    void shouldFreezeAuthenticatedMerchantAndAccountForTransactionExport() {
        MerchantDataExportTaskClient dataClient = mock(MerchantDataExportTaskClient.class);
        MerchantExportTaskApplicationService service = new MerchantExportTaskApplicationService(
                dataClient, mock(MerchantFinanceService.class));
        InternalAuthContextHolder.set(account("M1001", 88L));
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("M9999");
        query.setMerchantOrderNo("ORDER-1001");
        TransactionSubmitRequest request = new TransactionSubmitRequest();
        request.setRequestId(" request-1001 ");
        request.setQuery(query);
        TaskSummary summary = new TaskSummary();
        summary.setTaskNo("EXP1001");
        when(dataClient.submit(any())).thenReturn(summary);

        TaskSummary result = service.submitTransactions(request);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(dataClient).submit(captor.capture());
        CreateRequest command = captor.getValue();
        assertThat(result.getTaskNo()).isEqualTo("EXP1001");
        assertThat(command.getRequestId()).isEqualTo("request-1001");
        assertThat(command.getCreatorAccountId()).isEqualTo(88L);
        assertThat(command.getMerchantId()).isEqualTo("M1001");
        assertThat(command.getMerchantTransactionQuery().getMerchantOrderNo()).isEqualTo("ORDER-1001");
    }

    /**
     * 交易结算导出必须冻结当前商户并剔除页面分页参数，避免只导出当前页。
     */
    @Test
    void shouldFreezeSettlementTransactionQueryWithoutPageParameters() {
        MerchantDataExportTaskClient dataClient = mock(MerchantDataExportTaskClient.class);
        MerchantExportTaskApplicationService service = new MerchantExportTaskApplicationService(
                dataClient, mock(MerchantFinanceService.class));
        InternalAuthContextHolder.set(account("M1001", 88L));
        TransactionItemQuery query = new TransactionItemQuery();
        query.setSettlementBatchNo("ST20261110-001");
        query.setMerchantOrderNo("ORDER-2001");
        query.setPageNo(7);
        query.setPageSize(20);
        SettlementTransactionSubmitRequest request = new SettlementTransactionSubmitRequest();
        request.setRequestId(" request-settlement-1001 ");
        request.setQuery(query);
        when(dataClient.submit(any())).thenReturn(new TaskSummary());

        service.submitSettlementTransactions(request);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(dataClient).submit(captor.capture());
        CreateRequest command = captor.getValue();
        assertThat(command.getMerchantId()).isEqualTo("M1001");
        assertThat(command.getCreatorAccountId()).isEqualTo(88L);
        assertThat(command.getRequestId()).isEqualTo("request-settlement-1001");
        assertThat(command.getMerchantSettlementTransactionQuery().getSettlementBatchNo())
                .isEqualTo("ST20261110-001");
        assertThat(command.getMerchantSettlementTransactionQuery().getMerchantOrderNo())
                .isEqualTo("ORDER-2001");
        assertThat(command.getMerchantSettlementTransactionQuery().getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("pageNo", "pageSize", "merchantId");
    }

    /**
     * 缺少商户认证上下文时必须在调用 Data 前失败关闭。
     */
    @Test
    void shouldRejectExportWithoutAuthenticatedMerchant() {
        MerchantDataExportTaskClient dataClient = mock(MerchantDataExportTaskClient.class);
        MerchantExportTaskApplicationService service = new MerchantExportTaskApplicationService(
                dataClient, mock(MerchantFinanceService.class));
        TransactionSubmitRequest request = new TransactionSubmitRequest();
        request.setRequestId("request-1001");
        request.setQuery(new TransactionPageQuery());

        assertThatThrownBy(() -> service.submitTransactions(request))
                .isInstanceOf(ServiceException.class);
        verify(dataClient, never()).submit(any());
    }

    /**
     * 余额流水导出必须冻结认证商户当前资金账户，并剔除页面分页参数。
     */
    @Test
    void shouldFreezeMerchantFundAccountAndLedgerFilters() {
        MerchantDataExportTaskClient dataClient = mock(MerchantDataExportTaskClient.class);
        MerchantFinanceService financeService = mock(MerchantFinanceService.class);
        MerchantExportTaskApplicationService service = new MerchantExportTaskApplicationService(
                dataClient, financeService);
        InternalAuthContextHolder.set(account("M1001", 88L));
        FundAccountResponse fundAccount = new FundAccountResponse();
        fundAccount.setId(7001L);
        when(financeService.getFundAccount("M1001")).thenReturn(fundAccount);
        when(dataClient.submit(any())).thenReturn(new TaskSummary());
        DetailQuery query = new DetailQuery();
        query.setPageNo(7);
        query.setPageSize(50);
        query.setKeyword("LEDGER-1001");
        query.setBalanceType("AVAILABLE");
        FundLedgerSubmitRequest request = new FundLedgerSubmitRequest();
        request.setRequestId(" fund-ledger-request-001 ");
        request.setQuery(query);

        service.submitFundLedgers(request);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(dataClient).submit(captor.capture());
        CreateRequest command = captor.getValue();
        assertThat(command.getMerchantId()).isEqualTo("M1001");
        assertThat(command.getCreatorAccountId()).isEqualTo(88L);
        assertThat(command.getMerchantFundLedgerQuery().getAccountId()).isEqualTo(7001L);
        assertThat(command.getMerchantFundLedgerQuery().getKeyword()).isEqualTo("LEDGER-1001");
        assertThat(command.getMerchantFundLedgerQuery().getBalanceType()).isEqualTo("AVAILABLE");
        assertThat(command.getMerchantFundLedgerQuery().getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("pageNo", "pageSize", "merchantId");
        verify(financeService).getFundAccount("M1001");
    }

    /**
     * 构造绑定商户号和账号主键的可信登录账号。
     */
    private InternalAuthAccount account(String merchantId, Long accountId) {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("MERCHANT");
        account.setMerchantId(merchantId);
        account.setAccountId(accountId);
        account.setLoginAccount("merchant-admin");
        account.setRealName("Merchant Admin");
        return account;
    }
}
