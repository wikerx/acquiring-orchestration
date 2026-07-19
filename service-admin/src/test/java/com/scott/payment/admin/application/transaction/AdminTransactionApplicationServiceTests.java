package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /**
     * 退款动作应回填原交易上下文，并生成后台幂等请求号后调用支付核心。
     */
    @Test
    void refundShouldBuildPaymentCoreCommand() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        AdminTransactionApplicationService service = buildService(paymentInternalClient);
        when(paymentInternalClient.detail("TX202607140001")).thenReturn(detail("TX202607140001", "PAYMENT"));
        TransactionActionResponse expected = actionResponse("TX202607140002", "REFUND");
        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        when(paymentInternalClient.refund(captor.capture())).thenReturn(expected);

        TransactionActionRequest request = new TransactionActionRequest();
        request.setAmount(new BigDecimal("1.23"));
        request.setReason("manual refund");
        TransactionActionResponse actual = service.refund("TX202607140001", request);

        assertThat(actual).isSameAs(expected);
        PaymentTransactionActionClientRequestDTO command = captor.getValue();
        assertThat(command.getMerchantId()).isEqualTo("M10001");
        assertThat(command.getMerchantOrderNo()).isEqualTo("MO202607140001");
        assertThat(command.getMerchantOrderId()).startsWith("ADMRF");
        assertThat(command.getAmount()).isEqualByComparingTo("1.23");
        assertThat(command.getCurrency()).isEqualTo("USD");
        assertThat(command.getTransactionInfo().getSourceTransactionId()).isEqualTo("TX202607140001");
        assertThat(command.getTransactionInfo().getDescription()).isEqualTo("manual refund");
    }

    /**
     * 撤销动作应复用原交易上下文，并把状态机判断交给支付核心。
     */
    @Test
    void voidShouldBuildPaymentCoreCommand() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        AdminTransactionApplicationService service = buildService(paymentInternalClient);
        when(paymentInternalClient.detail("TX202607140010")).thenReturn(detail("TX202607140010", "AUTHORIZATION"));
        TransactionActionResponse expected = actionResponse("TX202607140011", "VOID");
        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        when(paymentInternalClient.voidPayment(captor.capture())).thenReturn(expected);

        TransactionActionResponse actual = service.voidPayment("TX202607140010", null);

        assertThat(actual).isSameAs(expected);
        PaymentTransactionActionClientRequestDTO command = captor.getValue();
        assertThat(command.getMerchantOrderId()).startsWith("ADMVD");
        assertThat(command.getAmount()).isNull();
        assertThat(command.getCurrency()).isEqualTo("USD");
        assertThat(command.getTransactionInfo().getSourceTransactionId()).isEqualTo("TX202607140010");
    }

    /**
     * 退款金额缺失或小于等于零时应在后台入口提前拒绝，避免无意义请求进入支付核心。
     */
    @Test
    void refundShouldRejectInvalidAmount() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        AdminTransactionApplicationService service = buildService(paymentInternalClient);
        when(paymentInternalClient.detail("TX202607140001")).thenReturn(detail("TX202607140001", "PAYMENT"));
        TransactionActionRequest request = new TransactionActionRequest();
        request.setAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.refund("TX202607140001", request))
                .isInstanceOf(ApiException.class);
    }

    /**
     * 用户在授权行发起退款时，后台应选择同生命周期内成功的请款或支付动作作为退款源。
     */
    @Test
    void refundShouldUseCapturedOperationWhenAuthorizationRowSelected() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        AdminTransactionApplicationService service = buildService(paymentInternalClient);
        TransactionDetailResponse detailResponse = new TransactionDetailResponse();
        TransactionOperationResponse authorization = operation("TX202607140020", "AUTHORIZATION", LocalDateTime.of(2026, 7, 14, 10, 0));
        TransactionOperationResponse capture = operation("TX202607140021", "CAPTURE", LocalDateTime.of(2026, 7, 14, 10, 5));
        detailResponse.setOperations(List.of(authorization, capture));
        when(paymentInternalClient.detail("TX202607140020")).thenReturn(detailResponse);
        ArgumentCaptor<PaymentTransactionActionClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentTransactionActionClientRequestDTO.class);
        when(paymentInternalClient.refund(captor.capture())).thenReturn(actionResponse("TX202607140022", "REFUND"));
        TransactionActionRequest request = new TransactionActionRequest();
        request.setAmount(new BigDecimal("1.00"));

        service.refund("TX202607140020", request);

        assertThat(captor.getValue().getTransactionInfo().getSourceTransactionId()).isEqualTo("TX202607140021");
    }

    private AdminTransactionApplicationService buildService(PaymentInternalClient paymentInternalClient) {
        return new AdminTransactionApplicationService(
                paymentInternalClient,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class)
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
