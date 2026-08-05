package com.scott.payment.merchant.application.transaction;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import com.scott.payment.merchant.client.payment.PaymentInternalClient;
import com.scott.payment.merchant.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionRequest;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import com.scott.payment.merchant.service.MerchantTransactionQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** 测试中代表当前认证商户的固定标识。 */
    private static final String MERCHANT_ID = "merchant-a";
    /** 测试中代表后续动作目标交易的固定标识。 */
    private static final String TRANSACTION_ID = "transaction-a";
    private static final LocalDateTime TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 7, 14, 12, 30, 45);
    private static final LocalDateTime ROOT_TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 4, 10, 9, 15, 30);

    @Test
    void voidShouldForwardSourceAndRootShardingTimesToPaymentCore() {
        MerchantTransactionQueryService queryService = mock(MerchantTransactionQueryService.class);
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        MerchantTransactionApplicationService service = service(
                paymentInternalClient, queryService, new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
        TransactionOrderResponse order = new TransactionOrderResponse();
        order.setMerchantId(MERCHANT_ID);
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setMerchantId(MERCHANT_ID);
        operation.setMerchantOrderNo("merchant-order-a");
        operation.setTransactionId(TRANSACTION_ID);
        operation.setTransactionType("AUTHORIZATION");
        operation.setTransactionStatus("SUCCESS");
        operation.setTransactionCurrency("USD");
        operation.setTransactionAmount(new BigDecimal("3.00"));
        operation.setTransactionDateTime(TRANSACTION_DATE_TIME);
        operation.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);
        TransactionDetailResponse detail = new TransactionDetailResponse();
        detail.setOrder(order);
        detail.setOperations(List.of(operation));
        when(queryService.detail(
                MERCHANT_ID, TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(detail);
        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> commandCaptor =
                ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        when(paymentInternalClient.voidPayment(commandCaptor.capture()))
                .thenReturn(new TransactionActionResponse());
        TransactionActionRequest request = new TransactionActionRequest();
        request.setTransactionDateTime(TRANSACTION_DATE_TIME);
        request.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);

        service.voidPayment(MERCHANT_ID, TRANSACTION_ID, request);

        PaymentTransactionActionClientRequestDTO.TransactionInfoDTO transactionInfo =
                commandCaptor.getValue().getTransactionInfo();
        assertThat(transactionInfo.getSourceTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(transactionInfo.getSourceTransactionDateTime()).isEqualTo(TRANSACTION_DATE_TIME);
        assertThat(transactionInfo.getRootTransactionDateTime()).isEqualTo(ROOT_TRANSACTION_DATE_TIME);
    }

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
    void exportShouldIgnoreQueryResultLimitAndStreamRows() throws Exception {
        MerchantTransactionQueryService queryService = mock(MerchantTransactionQueryService.class);
        RedisConcurrencyLimiter limiter = mock(RedisConcurrencyLimiter.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.getQueryBudget().setMaxResultRows(1);
        when(limiter.execute(anyString(), anyString(), anyString(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(5, Runnable.class).run();
                    return true;
        });
        TransactionOperationSearchResponse response = new TransactionOperationSearchResponse();
        response.setPage(PageResult.of(2L, 1L, 500L, List.of(new TransactionOperationResponse())));
        when(queryService.searchOperations(any(TransactionPageQuery.class))).thenReturn(response);
        when(httpResponse.getWriter()).thenReturn(new PrintWriter(body));
        MerchantTransactionApplicationService service = service(queryService, properties, limiter);

        service.exportOrders("merchant-a", new TransactionPageQuery(), "operator", httpResponse);

        assertThat(body.toString()).contains("系统订单号");
    }

    private MerchantTransactionApplicationService service(MerchantTransactionQueryService queryService,
                                                          TransactionShardingProperties properties,
                                                          RedisConcurrencyLimiter limiter) {
        return service(mock(PaymentInternalClient.class), queryService, properties, limiter);
    }

    private MerchantTransactionApplicationService service(PaymentInternalClient paymentInternalClient,
                                                          MerchantTransactionQueryService queryService,
                                                          TransactionShardingProperties properties,
                                                          RedisConcurrencyLimiter limiter) {
        return new MerchantTransactionApplicationService(
                paymentInternalClient, queryService, properties, limiter);
    }
}
