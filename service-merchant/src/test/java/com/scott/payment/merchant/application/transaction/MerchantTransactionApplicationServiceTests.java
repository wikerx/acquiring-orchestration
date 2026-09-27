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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionApplicationServiceTests
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证商户交易命令身份隔离以及同步导出资源预算和分页边界
 * @status : create
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

    /** 商户后台授权动作必须调用 CAPTURE，不得误标为预授权完成。 */
    /** 弹窗额度必须扣除处理中退款，失败和已成功动作不得重复扣减。 */
    @Test
    void refundContextShouldExcludePendingRefundsAndUseLabelCurrency() {
        MerchantTransactionQueryService query = mock(MerchantTransactionQueryService.class);
        PaymentInternalClient payment = mock(PaymentInternalClient.class);
        MerchantTransactionApplicationService service = service(payment, query, new TransactionShardingProperties(), mock(RedisConcurrencyLimiter.class));
        TransactionDetailResponse detail = detail("PAYMENT");
        TransactionOperationResponse source = detail.getOperations().get(0);
        source.setTransactionAmount(new BigDecimal("100.00"));
        source.setLabelAmount(new BigDecimal("124.68"));
        source.setLabelCurrency("HKD");
        source.setRefundedAmount(new BigDecimal("10.00"));
        source.setAvailableRefundAmount(new BigDecimal("90.00"));
        TransactionOperationResponse pending = refundOperation("PENDING", "20.00");
        TransactionOperationResponse processing = refundOperation("PROCESSING", "5.00");
        detail.setOperations(List.of(source, pending, processing,
                refundOperation("SUCCESS", "10.00"), refundOperation("FAILED", "50.00")));
        when(query.detail(MERCHANT_ID, TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME)).thenReturn(detail);
        var context = service.refundContext(MERCHANT_ID, TRANSACTION_ID, actionRequest());
        assertThat(context.currency()).isEqualTo("HKD");
        assertThat(context.currencyExponent()).isEqualTo(2);
        assertThat(context.refundedAmount()).isEqualByComparingTo("12.47");
        assertThat(context.pendingRefundAmount()).isEqualByComparingTo("31.17");
        assertThat(context.availableRefundAmount()).isEqualByComparingTo("81.04");
        verifyNoInteractions(payment);
    }

    /** 可退金额为零时不制造最小额度；日元与三位辅币按 ISO 精度显示。 */
    @Test
    void refundContextShouldHandleZeroBalanceAndCurrencyPrecision() {
        MerchantTransactionQueryService query = mock(MerchantTransactionQueryService.class);
        PaymentInternalClient payment = mock(PaymentInternalClient.class);
        MerchantTransactionApplicationService service = service(payment, query, new TransactionShardingProperties(), mock(RedisConcurrencyLimiter.class));
        TransactionDetailResponse detail = detail("PAYMENT");
        TransactionOperationResponse source = detail.getOperations().get(0);
        source.setTransactionAmount(new BigDecimal("100"));
        source.setAvailableRefundAmount(new BigDecimal("10"));
        source.setLabelAmount(new BigDecimal("123.456"));
        source.setLabelCurrency("KWD");
        when(query.detail(MERCHANT_ID, TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME)).thenReturn(detail);
        assertThat(service.refundContext(MERCHANT_ID, TRANSACTION_ID, actionRequest()).availableRefundAmount()).isEqualByComparingTo("12.345");
        source.setLabelCurrency("JPY");
        assertThat(service.refundContext(MERCHANT_ID, TRANSACTION_ID, actionRequest()).currencyExponent()).isZero();
        assertThat(service.refundContext(MERCHANT_ID, TRANSACTION_ID, actionRequest()).availableRefundAmount()).isEqualByComparingTo("12");
        detail.setOperations(List.of(source, refundOperation("PENDING", "11")));
        assertThat(service.refundContext(MERCHANT_ID, TRANSACTION_ID, actionRequest()).availableRefundAmount()).isZero();
    }

    /** 两端入口都必须拦截 OTHER 的空白说明，且不得调用支付核心。 */
    @Test
    void refundShouldRejectOtherWithoutDetailsBeforeCallingPayment() {
        MerchantTransactionQueryService query = mock(MerchantTransactionQueryService.class);
        PaymentInternalClient payment = mock(PaymentInternalClient.class);
        MerchantTransactionApplicationService service = service(payment, query, new TransactionShardingProperties(), mock(RedisConcurrencyLimiter.class));
        when(query.detail(MERCHANT_ID, TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME)).thenReturn(detail("PAYMENT"));
        TransactionActionRequest request = actionRequest();
        request.setAmount(new BigDecimal("1.00"));
        request.setReasonCode("OTHER");
        request.setRefundDescription(" \t\n");
        request.setReason("Other - cannot bypass required details");
        assertThatThrownBy(() -> service.refund(MERCHANT_ID, TRANSACTION_ID, request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(payment);
    }

    /** 带编码的部分退款原因和说明必须传入现有支付核心审计字段。 */
    @Test
    void refundShouldForwardStructuredReasonAndPreserveRequestId() {
        MerchantTransactionQueryService query = mock(MerchantTransactionQueryService.class);
        PaymentInternalClient payment = mock(PaymentInternalClient.class);
        MerchantTransactionApplicationService service = service(payment, query, new TransactionShardingProperties(), mock(RedisConcurrencyLimiter.class));
        when(query.detail(MERCHANT_ID, TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME)).thenReturn(detail("PAYMENT"));
        TransactionActionRequest request = actionRequest();
        request.setAmount(new BigDecimal("1.00"));
        request.setMerchantOrderId("refund-request-001");
        request.setReasonCode("OTHER");
        request.setRefundDescription("  Return one item  ");
        service.refund(MERCHANT_ID, TRANSACTION_ID, request);
        service.refund(MERCHANT_ID, TRANSACTION_ID, request);
        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> captor = ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        org.mockito.Mockito.verify(payment, org.mockito.Mockito.times(2)).refund(captor.capture());
        assertThat(captor.getValue().getRequestReason()).endsWith(" - Return one item");
        assertThat(captor.getValue().getTransactionInfo().getDescription()).isEqualTo(captor.getValue().getRequestReason());
        assertThat(captor.getValue().getMerchantOrderId()).isEqualTo("refund-request-001");
        assertThat(captor.getAllValues()).allSatisfy(command ->
                assertThat(command.getMerchantOrderId()).isEqualTo("refund-request-001"));
    }

    /** 退款额度查询不能泄露其他商户交易信息。 */
    @Test
    void refundContextShouldRejectAnotherMerchant() {
        MerchantTransactionQueryService query = mock(MerchantTransactionQueryService.class);
        PaymentInternalClient payment = mock(PaymentInternalClient.class);
        MerchantTransactionApplicationService service = service(payment, query,
                new TransactionShardingProperties(), mock(RedisConcurrencyLimiter.class));
        TransactionDetailResponse detail = detail("PAYMENT");
        detail.getOrder().setMerchantId("another-merchant");
        when(query.detail(MERCHANT_ID, TRANSACTION_ID, TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME)).thenReturn(detail);
        assertThatThrownBy(() -> service.refundContext(MERCHANT_ID, TRANSACTION_ID, actionRequest()))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(payment);
    }

    private TransactionOperationResponse refundOperation(String status, String amount) {
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setMerchantId(MERCHANT_ID);
        operation.setTransactionType("REFUND");
        operation.setTransactionStatus(status);
        operation.setTransactionAmount(new BigDecimal(amount));
        return operation;
    }

    @Test
    void captureShouldCallCaptureCommandForAuthorization() {
        MerchantTransactionQueryService queryService = mock(MerchantTransactionQueryService.class);
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        MerchantTransactionApplicationService service = service(
                paymentInternalClient, queryService, new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
        when(queryService.detail(MERCHANT_ID, TRANSACTION_ID,
                TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(detail("AUTHORIZATION"));
        when(paymentInternalClient.capture(any())).thenReturn(new TransactionActionResponse());

        service.capture(MERCHANT_ID, TRANSACTION_ID, actionRequest());

        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        verify(paymentInternalClient).capture(captor.capture());
        assertThat(captor.getValue().getMerchantOrderId()).startsWith("MCHCP");
    }

    /** 商户后台预授权动作必须调用 PRE_AUTH_COMPLETION 独立支付核心命令。 */
    @Test
    void preAuthCompletionShouldCallDedicatedCommand() {
        MerchantTransactionQueryService queryService = mock(MerchantTransactionQueryService.class);
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        MerchantTransactionApplicationService service = service(
                paymentInternalClient, queryService, new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
        when(queryService.detail(MERCHANT_ID, TRANSACTION_ID,
                TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(detail("PRE_AUTHORIZATION"));
        when(paymentInternalClient.preAuthCompletion(any())).thenReturn(new TransactionActionResponse());

        service.preAuthCompletion(MERCHANT_ID, TRANSACTION_ID, actionRequest());

        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        verify(paymentInternalClient).preAuthCompletion(captor.capture());
        assertThat(captor.getValue().getMerchantOrderId()).startsWith("MCHPA");
    }

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

    private TransactionDetailResponse detail(String transactionType) {
        TransactionOrderResponse order = new TransactionOrderResponse();
        order.setMerchantId(MERCHANT_ID);
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setMerchantId(MERCHANT_ID);
        operation.setMerchantOrderNo("merchant-order-a");
        operation.setTransactionId(TRANSACTION_ID);
        operation.setTransactionType(transactionType);
        operation.setTransactionStatus("SUCCESS");
        operation.setTransactionCurrency("USD");
        operation.setTransactionAmount(new BigDecimal("3.00"));
        operation.setTransactionDateTime(TRANSACTION_DATE_TIME);
        operation.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);
        TransactionDetailResponse detail = new TransactionDetailResponse();
        detail.setOrder(order);
        detail.setOperations(List.of(operation));
        return detail;
    }

    private TransactionActionRequest actionRequest() {
        TransactionActionRequest request = new TransactionActionRequest();
        request.setTransactionDateTime(TRANSACTION_DATE_TIME);
        request.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);
        return request;
    }
}
