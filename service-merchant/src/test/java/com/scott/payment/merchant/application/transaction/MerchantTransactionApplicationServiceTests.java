package com.scott.payment.merchant.application.transaction;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import com.scott.payment.merchant.client.payment.PaymentInternalClient;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import com.scott.payment.merchant.service.MerchantTransactionQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商户交易同步导出资源预算测试。
 */
class MerchantTransactionApplicationServiceTests {

    @Test
    void exportShouldRejectWhenMerchantAccountConcurrencyBudgetIsFull() {
        MerchantTransactionQueryService queryService = mock(MerchantTransactionQueryService.class);
        RedisConcurrencyLimiter limiter = mock(RedisConcurrencyLimiter.class);
        MerchantTransactionApplicationService service = service(
                queryService, new TransactionShardingProperties(), limiter);
        when(limiter.execute(anyString(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.exportOrders(
                "merchant-a", new TransactionPageQuery(), "operator", mock(HttpServletResponse.class)))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(queryService);
    }

    @Test
    void exportShouldApplyConfiguredMaximumResultRows() {
        MerchantTransactionQueryService queryService = mock(MerchantTransactionQueryService.class);
        RedisConcurrencyLimiter limiter = mock(RedisConcurrencyLimiter.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.getQueryBudget().setMaxResultRows(1);
        when(limiter.execute(anyString(), anyString(), anyString(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(5, Runnable.class).run();
                    return true;
                });
        TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
        response.setPage(PageResult.of(2L, 1L, 1L, List.of()));
        when(queryService.searchOperations(any(TransactionPageQuery.class))).thenReturn(response);
        MerchantTransactionApplicationService service = service(queryService, properties, limiter);

        assertThatThrownBy(() -> service.exportOrders(
                "merchant-a", new TransactionPageQuery(), "operator", mock(HttpServletResponse.class)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("asynchronous export");
    }

    private MerchantTransactionApplicationService service(MerchantTransactionQueryService queryService,
                                                          TransactionShardingProperties properties,
                                                          RedisConcurrencyLimiter limiter) {
        return new MerchantTransactionApplicationService(
                mock(PaymentInternalClient.class), queryService, properties, limiter);
    }
}
