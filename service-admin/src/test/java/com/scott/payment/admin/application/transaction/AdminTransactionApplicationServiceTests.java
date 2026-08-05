package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionApplicationServiceTests
 * @date : 2026-07-14 23:59
 * @email : scott_x@163.com
 * @description : 管理后台交易应用服务单元测试，验证退款、撤销等后台动作只编排内部请求，不直接修改交易事实表。
 * @status : create
 */
class AdminTransactionApplicationServiceTests {

    private static final LocalDateTime TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 7, 14, 12, 30, 45);
    private static final LocalDateTime ROOT_TRANSACTION_DATE_TIME =
            LocalDateTime.of(2026, 4, 10, 9, 15, 30);

    /**
     * 退款动作应回填原交易上下文，并生成后台幂等请求号后调用支付核心。
     */
    @Test
    void refundShouldBuildPaymentCoreCommand() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        AdminTransactionQueryService transactionQueryService = mock(AdminTransactionQueryService.class);
        AdminTransactionApplicationService service = buildService(paymentInternalClient, transactionQueryService);
        when(transactionQueryService.detail(
                "TX202607140001", TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(detail("TX202607140001", "PAYMENT"));
        TransactionActionResponse expected = actionResponse("TX202607140002", "REFUND");
        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        when(paymentInternalClient.refund(captor.capture())).thenReturn(expected);

        TransactionActionRequest request = new TransactionActionRequest();
        request.setAmount(new BigDecimal("1.23"));
        request.setReason("manual refund");
        request.setTransactionDateTime(TRANSACTION_DATE_TIME);
        request.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);
        TransactionActionResponse actual = service.refund("TX202607140001", request);

        assertThat(actual).isSameAs(expected);
        PaymentTransactionActionClientRequestDTO command = captor.getValue();
        assertThat(command.getMerchantId()).isEqualTo("M10001");
        assertThat(command.getMerchantOrderNo()).isEqualTo("MO202607140001");
        assertThat(command.getMerchantOrderId()).startsWith("ADMRF");
        assertThat(command.getAmount()).isEqualByComparingTo("1.23");
        assertThat(command.getCurrency()).isEqualTo("USD");
        assertThat(command.getTransactionInfo().getSourceTransactionId()).isEqualTo("TX202607140001");
        assertThat(command.getTransactionInfo().getSourceTransactionDateTime())
                .isEqualTo(TRANSACTION_DATE_TIME);
        assertThat(command.getTransactionInfo().getRootTransactionDateTime())
                .isEqualTo(ROOT_TRANSACTION_DATE_TIME);
        assertThat(command.getTransactionInfo().getDescription()).isEqualTo("manual refund");
    }

    /**
     * 撤销动作应复用原交易上下文，并把状态机判断交给支付核心。
     */
    @Test
    void voidShouldBuildPaymentCoreCommand() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        AdminTransactionQueryService transactionQueryService = mock(AdminTransactionQueryService.class);
        AdminTransactionApplicationService service = buildService(paymentInternalClient, transactionQueryService);
        when(transactionQueryService.detail(
                "TX202607140010", TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(detail("TX202607140010", "AUTHORIZATION"));
        TransactionActionResponse expected = actionResponse("TX202607140011", "VOID");
        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        when(paymentInternalClient.voidPayment(captor.capture())).thenReturn(expected);

        TransactionActionRequest request = new TransactionActionRequest();
        request.setTransactionDateTime(TRANSACTION_DATE_TIME);
        request.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);
        TransactionActionResponse actual = service.voidPayment("TX202607140010", request);

        assertThat(actual).isSameAs(expected);
        PaymentTransactionActionClientRequestDTO command = captor.getValue();
        assertThat(command.getMerchantOrderId()).startsWith("ADMVD");
        assertThat(command.getAmount()).isEqualByComparingTo("3.00");
        assertThat(command.getLabelAmount()).isEqualByComparingTo("3.00");
        assertThat(command.getCurrency()).isEqualTo("USD");
        assertThat(command.getTransactionInfo().getSourceTransactionId()).isEqualTo("TX202607140010");
        assertThat(command.getTransactionInfo().getSourceTransactionDateTime())
                .isEqualTo(TRANSACTION_DATE_TIME);
        assertThat(command.getTransactionInfo().getRootTransactionDateTime())
                .isEqualTo(ROOT_TRANSACTION_DATE_TIME);
    }

    /**
     * 退款金额缺失或小于等于零时应在后台入口提前拒绝，避免无意义请求进入支付核心。
     */
    @Test
    void refundShouldRejectInvalidAmount() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        AdminTransactionQueryService transactionQueryService = mock(AdminTransactionQueryService.class);
        AdminTransactionApplicationService service = buildService(paymentInternalClient, transactionQueryService);
        when(transactionQueryService.detail(
                "TX202607140001", TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(detail("TX202607140001", "PAYMENT"));
        TransactionActionRequest request = new TransactionActionRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setTransactionDateTime(TRANSACTION_DATE_TIME);
        request.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);

        assertThatThrownBy(() -> service.refund("TX202607140001", request))
                .isInstanceOf(ApiException.class);
    }

    /**
     * 授权成功交易不支持直接退款，需先请款成功后再按请款交易退款。
     */
    @Test
    void refundShouldRejectAuthorizationOperation() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        AdminTransactionQueryService transactionQueryService = mock(AdminTransactionQueryService.class);
        AdminTransactionApplicationService service = buildService(paymentInternalClient, transactionQueryService);
        when(transactionQueryService.detail(
                "TX202607140020", TRANSACTION_DATE_TIME, ROOT_TRANSACTION_DATE_TIME))
                .thenReturn(detail("TX202607140020", "AUTHORIZATION"));
        TransactionActionRequest request = new TransactionActionRequest();
        request.setAmount(new BigDecimal("1.00"));
        request.setTransactionDateTime(TRANSACTION_DATE_TIME);
        request.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);

        assertThatThrownBy(() -> service.refund("TX202607140020", request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void exportShouldRejectWhenAccountConcurrencyBudgetIsFull() {
        AdminTransactionQueryService queryService = mock(AdminTransactionQueryService.class);
        RedisConcurrencyLimiter limiter = mock(RedisConcurrencyLimiter.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        AdminTransactionApplicationService service = buildService(queryService, properties, limiter);
        when(limiter.execute(anyString(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.exportOrders(
                new TransactionPageQuery(), "operator", mock(HttpServletResponse.class)))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(queryService);
    }

    @Test
    void exportShouldIgnoreQueryResultLimitAndUsePagedWriter() {
        AdminTransactionQueryService queryService = mock(AdminTransactionQueryService.class);
        RedisConcurrencyLimiter limiter = mock(RedisConcurrencyLimiter.class);
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.getQueryBudget().setMaxResultRows(1);
        when(limiter.execute(anyString(), anyString(), anyString(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(5, Runnable.class).run();
                    return true;
                });
        when(queryService.pageOrders(any(TransactionPageQuery.class)))
                .thenReturn(PageResult.of(2L, 1L, 1L, List.of()));
        when(localeResolver.resolveCurrentLocale()).thenReturn(Locale.ENGLISH);
        AdminTransactionApplicationService service = new AdminTransactionApplicationService(
                mock(PaymentInternalClient.class), queryService, excelExportService,
                mock(ExcelI18nMessageResolver.class), localeResolver, properties, limiter);

        service.exportOrders(new TransactionPageQuery(), "operator", mock(HttpServletResponse.class));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<ExcelPagedExportRequest> requestCaptor =
                ArgumentCaptor.forClass(ExcelPagedExportRequest.class);
        verify(excelExportService).exportPaged(requestCaptor.capture(), any(HttpServletResponse.class));
        ExcelPagedExportRequest<?> request = requestCaptor.getValue();
        assertThat(request.getPageSize()).isEqualTo(500);
        assertThat(request.getPageLoader().apply(1)).isEmpty();
    }

    private AdminTransactionApplicationService buildService(PaymentInternalClient paymentInternalClient) {
        return buildService(paymentInternalClient, mock(AdminTransactionQueryService.class));
    }

    private AdminTransactionApplicationService buildService(PaymentInternalClient paymentInternalClient,
                                                            AdminTransactionQueryService transactionQueryService) {
        return buildService(transactionQueryService, new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class), paymentInternalClient);
    }

    private AdminTransactionApplicationService buildService(AdminTransactionQueryService transactionQueryService,
                                                            TransactionShardingProperties properties,
                                                            RedisConcurrencyLimiter limiter) {
        return buildService(transactionQueryService, properties, limiter, mock(PaymentInternalClient.class));
    }

    private AdminTransactionApplicationService buildService(AdminTransactionQueryService transactionQueryService,
                                                            TransactionShardingProperties properties,
                                                            RedisConcurrencyLimiter limiter,
                                                            PaymentInternalClient paymentInternalClient) {
        return new AdminTransactionApplicationService(
                paymentInternalClient, transactionQueryService,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                properties,
                limiter
        );
    }

    private TransactionDetailResponse detail(String transactionId, String transactionType) {
        TransactionOperationResponse operation = operation(transactionId, transactionType, null);
        TransactionDetailResponse detailResponse = new TransactionDetailResponse();
        detailResponse.setOperations(List.of(operation));
        return detailResponse;
    }

    private TransactionOperationResponse operation(String transactionId, String transactionType, LocalDateTime operationTime) {
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setTransactionId(transactionId);
        operation.setMerchantId("M10001");
        operation.setMerchantOrderNo("MO202607140001");
        operation.setMerchantOrderId("MERCHANT_REQ_001");
        operation.setTransactionType(transactionType);
        operation.setTransactionStatus("SUCCESS");
        operation.setTransactionCurrency("USD");
        operation.setTransactionAmount(new BigDecimal("3.00"));
        operation.setTransactionDateTime(TRANSACTION_DATE_TIME);
        operation.setRootTransactionDateTime(ROOT_TRANSACTION_DATE_TIME);
        operation.setOperationTime(operationTime);
        return operation;
    }

    private TransactionActionResponse actionResponse(String transactionId, String transactionType) {
        TransactionActionResponse response = new TransactionActionResponse();
        response.setTransactionId(transactionId);
        response.setTransactionType(transactionType);
        response.setStatus("SUCCESS");
        return response;
    }
}
