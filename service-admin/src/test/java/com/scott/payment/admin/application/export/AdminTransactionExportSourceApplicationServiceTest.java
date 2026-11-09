package com.scott.payment.admin.application.export;

import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OrderPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OperationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.MerchantNotificationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.RefundPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.TransactionOrderQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundRecord;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminRefundQueryService;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.core.model.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交易域异步导出来源测试，验证冻结范围、内部分页和安全行投影。
 */
class AdminTransactionExportSourceApplicationServiceTest {

    /**
     * 交易主单来源必须使用冻结商户集合和内部页码，并完整投影金额与状态字段。
     */
    @Test
    void shouldPageOrdersWithinFrozenMerchantScope() {
        AdminTransactionQueryService transactionService = mock(AdminTransactionQueryService.class);
        AdminRefundQueryService refundService = mock(AdminRefundQueryService.class);
        TransactionOrderResponse source = new TransactionOrderResponse();
        source.setRootTransactionId("ROOT-1001");
        source.setLatestTransactionId("TXN-1002");
        source.setMerchantId("M1001");
        source.setCurrentAmount(new BigDecimal("88.50"));
        source.setCurrentCurrency("EUR");
        source.setTransactionStatus("SUCCESS");
        when(transactionService.pageOrdersForExport(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.of(1L, 3L, 50L, List.of(source)));
        AdminTransactionExportSourceApplicationService service =
                new AdminTransactionExportSourceApplicationService(transactionService, refundService);
        OrderPageRequest request = new OrderPageRequest();
        request.setCreatorAccountId(88L);
        request.setAllMerchants(false);
        request.setMerchantIds(Set.of("M1001", "M1002"));
        var query = new TransactionOrderQuery();
        query.setMerchantOrderNo("ORDER-1001");
        request.setQuery(query);
        request.setPageNo(3);
        request.setPageSize(50);

        var response = service.pageOrders(request);

        ArgumentCaptor<TransactionPageQuery> queryCaptor =
                ArgumentCaptor.forClass(TransactionPageQuery.class);
        ArgumentCaptor<AdminMerchantDataScope> scopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(transactionService).pageOrdersForExport(queryCaptor.capture(), scopeCaptor.capture());
        assertThat(queryCaptor.getValue().getMerchantOrderNo()).isEqualTo("ORDER-1001");
        assertThat(queryCaptor.getValue().getPageNo()).isEqualTo(3);
        assertThat(queryCaptor.getValue().getPageSize()).isEqualTo(50);
        assertThat(scopeCaptor.getValue().allMerchants()).isFalse();
        assertThat(scopeCaptor.getValue().merchantIds()).containsExactlyInAnyOrder("M1001", "M1002");
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getRootTransactionId()).isEqualTo("ROOT-1001");
            assertThat(row.getLatestTransactionId()).isEqualTo("TXN-1002");
            assertThat(row.getCurrentAmount()).isEqualByComparingTo("88.50");
            assertThat(row.getCurrentCurrency()).isEqualTo("EUR");
            assertThat(row.getTransactionStatus()).isEqualTo("SUCCESS");
        });
    }

    /**
     * 交易操作来源必须使用冻结商户集合，并仅投影脱敏支付工具和运营排查字段。
     */
    @Test
    void shouldPageOperationsWithinFrozenMerchantScope() {
        AdminTransactionQueryService transactionService = mock(AdminTransactionQueryService.class);
        AdminRefundQueryService refundService = mock(AdminRefundQueryService.class);
        TransactionOperationResponse source = new TransactionOperationResponse();
        source.setTransactionId("TXN-3001");
        source.setMerchantId("M3001");
        source.setTransactionAmount(new BigDecimal("15.25"));
        source.setTransactionCurrency("GBP");
        source.setPaymentMethod("CARD");
        source.setCardNumberMasked("411111******1111");
        source.setChannelResponseMessage("sanitized channel summary");
        when(transactionService.pageOperationsForExport(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.of(1L, 2L, 50L, List.of(source)));
        AdminTransactionExportSourceApplicationService service =
                new AdminTransactionExportSourceApplicationService(transactionService, refundService);
        OperationPageRequest request = new OperationPageRequest();
        request.setCreatorAccountId(88L);
        request.setAllMerchants(false);
        request.setMerchantIds(Set.of("M3001"));
        TransactionOrderQuery query = new TransactionOrderQuery();
        query.setTransactionId("TXN-3001");
        request.setQuery(query);
        request.setPageNo(2);
        request.setPageSize(50);

        var response = service.pageOperations(request);

        ArgumentCaptor<TransactionPageQuery> queryCaptor = ArgumentCaptor.forClass(TransactionPageQuery.class);
        ArgumentCaptor<AdminMerchantDataScope> scopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(transactionService).pageOperationsForExport(queryCaptor.capture(), scopeCaptor.capture());
        assertThat(queryCaptor.getValue().getTransactionId()).isEqualTo("TXN-3001");
        assertThat(queryCaptor.getValue().getPageNo()).isEqualTo(2);
        assertThat(scopeCaptor.getValue().merchantIds()).containsExactly("M3001");
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getTransactionAmount()).isEqualByComparingTo("15.25");
            assertThat(row.getTransactionCurrency()).isEqualTo("GBP");
            assertThat(row.getCardNumberMasked()).isEqualTo("411111******1111");
            assertThat(row.getChannelResponseMessage()).isEqualTo("sanitized channel summary");
        });
    }

    /**
     * 商户通知来源必须使用冻结商户集合，且安全投影不包含地址、载荷或响应正文。
     */
    @Test
    void shouldPageMerchantNotificationsWithinFrozenMerchantScope() {
        AdminTransactionQueryService transactionService = mock(AdminTransactionQueryService.class);
        AdminRefundQueryService refundService = mock(AdminRefundQueryService.class);
        MerchantNotificationResponse source = new MerchantNotificationResponse();
        source.setNotifyId("NTF-4001");
        source.setTransactionId("TXN-4001");
        source.setMerchantId("M4001");
        source.setNotifyStatus("FAILED");
        source.setTargetUrlMasked("https://merchant.example/***");
        source.setPayloadJsonMasked("{masked}");
        when(transactionService.pageMerchantNotificationsForExport(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.of(1L, 1L, 100L, List.of(source)));
        AdminTransactionExportSourceApplicationService service =
                new AdminTransactionExportSourceApplicationService(transactionService, refundService);
        MerchantNotificationPageRequest request = new MerchantNotificationPageRequest();
        request.setCreatorAccountId(88L);
        request.setAllMerchants(false);
        request.setMerchantIds(Set.of("M4001"));
        var query = new com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs
                .MerchantNotificationQuery();
        query.setTransactionId("TXN-4001");
        query.setNotifyStatus("FAILED");
        request.setQuery(query);
        request.setPageNo(1);
        request.setPageSize(100);

        var response = service.pageMerchantNotifications(request);

        ArgumentCaptor<com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery>
                queryCaptor = ArgumentCaptor.forClass(
                com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery.class);
        ArgumentCaptor<AdminMerchantDataScope> scopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(transactionService).pageMerchantNotificationsForExport(
                queryCaptor.capture(), scopeCaptor.capture());
        assertThat(queryCaptor.getValue().getTransactionId()).isEqualTo("TXN-4001");
        assertThat(queryCaptor.getValue().getNotifyStatus()).isEqualTo("FAILED");
        assertThat(scopeCaptor.getValue().merchantIds()).containsExactly("M4001");
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getNotifyId()).isEqualTo("NTF-4001");
            assertThat(row.getNotifyStatus()).isEqualTo("FAILED");
            assertThat(java.util.Arrays.stream(row.getClass().getDeclaredFields())
                    .map(java.lang.reflect.Field::getName))
                    .doesNotContain("targetUrlMasked", "payloadJsonMasked", "responseBody");
        });
    }

    /**
     * 退款来源必须保留全商户范围语义，并完整投影退款金额与审批状态。
     */
    @Test
    void shouldPageRefundsWithAllMerchantScope() {
        AdminTransactionQueryService transactionService = mock(AdminTransactionQueryService.class);
        AdminRefundQueryService refundService = mock(AdminRefundQueryService.class);
        RefundRecord source = new RefundRecord();
        source.setRefundTransactionId("RFD-2001");
        source.setSourceTransactionId("PAY-2001");
        source.setTransactionAmount(new BigDecimal("20.00"));
        source.setTransactionCurrency("USD");
        source.setApprovalStatus("APPROVED");
        when(refundService.pageForExport(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.of(1L, 1L, 100L, List.of(source)));
        AdminTransactionExportSourceApplicationService service =
                new AdminTransactionExportSourceApplicationService(transactionService, refundService);
        RefundPageRequest request = new RefundPageRequest();
        request.setCreatorAccountId(88L);
        request.setAllMerchants(true);
        request.setMerchantIds(Set.of());
        var query = new com.scott.payment.admin.dto.exporttask
                .AdminTransactionExportInternalDTOs.RefundQuery();
        query.setRefundTransactionId("RFD-2001");
        request.setQuery(query);
        request.setPageNo(1);
        request.setPageSize(100);

        var response = service.pageRefunds(request);

        ArgumentCaptor<com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery> queryCaptor =
                ArgumentCaptor.forClass(com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery.class);
        ArgumentCaptor<AdminMerchantDataScope> scopeCaptor =
                ArgumentCaptor.forClass(AdminMerchantDataScope.class);
        verify(refundService).pageForExport(queryCaptor.capture(), scopeCaptor.capture());
        assertThat(queryCaptor.getValue().getRefundTransactionId()).isEqualTo("RFD-2001");
        assertThat(scopeCaptor.getValue().allMerchants()).isTrue();
        assertThat(response.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getRefundTransactionId()).isEqualTo("RFD-2001");
            assertThat(row.getSourceTransactionId()).isEqualTo("PAY-2001");
            assertThat(row.getTransactionAmount()).isEqualByComparingTo("20.00");
            assertThat(row.getTransactionCurrency()).isEqualTo("USD");
            assertThat(row.getApprovalStatus()).isEqualTo("APPROVED");
        });
    }
}
