package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.ChannelTransactionStatusResolver;
import com.scott.payment.payment.service.PaymentChannelInvokeService;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.RefundChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.ChannelTransactionStatusResolution;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultApprovedRefundChannelExecutorTests
 * @date : 2026-08-06
 * @email : scott_x@163.com
 * @description : 已批准退款渠道执行测试，锁定固定渠道恢复、请求身份复用和未知结果非终态语义。
 * @status : create
 */
class DefaultApprovedRefundChannelExecutorTests {

    /** 审批后的首次执行必须复用受理事务保存的渠道和请求身份。 */
    @Test
    void shouldRestoreFixedRouteAndReusePreparedRequestIdentity() {
        Fixture fixture = new Fixture();
        PaymentChannelInvokeResultDTO invokeResult = fixture.successInvokeResult();
        when(fixture.channelInvokeService.invoke(any(), same(fixture.route), eq("OP-1"), eq("RF-1"), any(PaymentPreparedChannelRequestDTO.class)))
                .thenReturn(invokeResult);
        ChannelTransactionStatusResolution resolution = new ChannelTransactionStatusResolution();
        resolution.setTargetStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        resolution.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        when(fixture.statusResolver.resolveSync("MPGS", "REFUND", invokeResult.getChannelResponse()))
                .thenReturn(resolution);

        fixture.executor.execute(fixture.operation, fixture.request, fixture.message);

        verify(fixture.routeService).restore("MPGS", 11L, 22L, "MID-TERM-1");
        ArgumentCaptor<PaymentPreparedChannelRequestDTO> preparedCaptor =
                ArgumentCaptor.forClass(PaymentPreparedChannelRequestDTO.class);
        verify(fixture.channelInvokeService).invoke(
                any(), same(fixture.route), eq("OP-1"), eq("RF-1"), preparedCaptor.capture());
        assertThat(preparedCaptor.getValue().getRequestId()).isEqualTo("REQ-1");
        assertThat(preparedCaptor.getValue().getChannelOrderNo()).isEqualTo("CHANNEL-ORDER-1");
        assertThat(preparedCaptor.getValue().getChannelTransactionId()).isEqualTo("CHANNEL-TX-1");

        RefundPreparationResultDTO preparation = fixture.capturedPreparation();
        assertThat(preparation.isCallChannel()).isFalse();
        assertThat(preparation.getResultDTO().getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(preparation.getResultDTO().getTotalRefundAmount()).isEqualByComparingTo("12.50");
        verify(fixture.idempotencyService).complete(
                eq("TRANSACTION_OPERATION"), eq("IDEMPOTENCY-KEY"), eq("OP-1"), eq("RF-1"),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), eq(new BigDecimal("2.50")), eq("USD"), any());
    }

    /** 渠道超时且结果未知时必须保留处理中，禁止误记失败。 */
    @Test
    void shouldKeepOutcomeUncertainTimeoutProcessing() {
        Fixture fixture = new Fixture();
        PaymentChannelInvokeResultDTO timeout = new PaymentChannelInvokeResultDTO();
        timeout.setRequestStatus("TIMEOUT");
        timeout.setExceptionType("SocketTimeoutException");
        timeout.setOutcomeUncertain(true);
        when(fixture.channelInvokeService.invoke(any(), same(fixture.route), eq("OP-1"), eq("RF-1"), any(PaymentPreparedChannelRequestDTO.class)))
                .thenReturn(timeout);

        fixture.executor.execute(fixture.operation, fixture.request, fixture.message);

        RefundPreparationResultDTO preparation = fixture.capturedPreparation();
        assertThat(preparation.getResultDTO().getStatus()).isEqualTo(PaymentTransactionStatusEnum.PROCESSING.getCode());
        assertThat(preparation.getResultDTO().getProcessStage()).isEqualTo(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        assertThat(preparation.getResultDTO().getPendingReasonCode()).isEqualTo("CHANNEL_RESULT_UNKNOWN");
        assertThat(preparation.getResultDTO().getFailReasonCode()).isNull();
        verify(fixture.statusResolver, never()).resolveSync(any(), any(), any());
        verify(fixture.idempotencyService).complete(
                eq("TRANSACTION_OPERATION"), eq("IDEMPOTENCY-KEY"), eq("OP-1"), eq("RF-1"),
                eq(PaymentTransactionStatusEnum.PROCESSING.getCode()), eq(new BigDecimal("2.50")), eq("USD"), any());
    }

    private static final class Fixture {

        private final LocalDateTime refundTime = LocalDateTime.of(2026, 8, 6, 10, 0);
        private final LocalDateTime sourceTime = refundTime.minusDays(1);
        private final TransactionRecordService recordService = mock(TransactionRecordService.class);
        private final PaymentChannelRouteService routeService = mock(PaymentChannelRouteService.class);
        private final PaymentChannelInvokeService channelInvokeService = mock(PaymentChannelInvokeService.class);
        private final ChannelTransactionStatusResolver statusResolver = mock(ChannelTransactionStatusResolver.class);
        private final RefundChannelResultTransactionService resultService = mock(RefundChannelResultTransactionService.class);
        private final TransactionIdempotencyService idempotencyService = mock(TransactionIdempotencyService.class);
        private final PaymentRouteResultDTO route = PaymentRouteResultDTO.routed("MPGS");
        private final TransactionOperationDO operation = operation();
        private final TransactionChannelRequestDO request = request();
        private final RefundExecutionMessage message = message();
        private final DefaultApprovedRefundChannelExecutor executor = new DefaultApprovedRefundChannelExecutor(
                recordService, routeService, channelInvokeService, statusResolver, resultService, idempotencyService);

        private Fixture() {
            TransactionOrderDO sourceOrder = new TransactionOrderDO();
            sourceOrder.setTransactionDateTime(sourceTime);
            sourceOrder.setPaymentMethod("BANK_CARD");
            sourceOrder.setPaymentBrand("VISA");
            sourceOrder.setRefundedAmount(new BigDecimal("10.00"));
            sourceOrder.setCapturedAmount(new BigDecimal("50.00"));
            TransactionOperationDO sourceOperation = new TransactionOperationDO();
            sourceOperation.setChannelTransactionId("SOURCE-CHANNEL-TX");
            when(recordService.findSourceOrderByTransactionId("PAY-1", sourceTime, sourceTime))
                    .thenReturn(sourceOrder);
            when(recordService.findSourceOperationByTransactionId("PAY-1", sourceTime))
                    .thenReturn(sourceOperation);
            when(routeService.restore("MPGS", 11L, 22L, "MID-TERM-1")).thenReturn(route);
            when(idempotencyService.buildTransactionOperationKey("M-1", "PAY-1:MR-1", "REFUND"))
                    .thenReturn("IDEMPOTENCY-KEY");
        }

        private PaymentChannelInvokeResultDTO successInvokeResult() {
            ChannelPaymentResponse response = new ChannelPaymentResponse();
            response.setChannelCode("MPGS");
            response.setChannelTradeStatus("SUCCESS");
            return PaymentChannelInvokeResultDTO.success(null, response);
        }

        private RefundPreparationResultDTO capturedPreparation() {
            ArgumentCaptor<RefundPreparationResultDTO> captor =
                    ArgumentCaptor.forClass(RefundPreparationResultDTO.class);
            verify(resultService).recordRefundChannelResult(captor.capture(), any());
            return captor.getValue();
        }

        private TransactionOperationDO operation() {
            TransactionOperationDO value = new TransactionOperationDO();
            value.setOperationId("OP-1");
            value.setTransactionId("RF-1");
            value.setSourceTransactionId("PAY-1");
            value.setMerchantId("M-1");
            value.setMerchantOrderNo("MO-1");
            value.setMerchantOperationNo("MR-1");
            value.setTransactionType("REFUND");
            value.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
            value.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
            value.setTransactionAmount(new BigDecimal("2.50"));
            value.setTransactionCurrency("USD");
            value.setLabelAmount(new BigDecimal("2.50"));
            value.setLabelCurrency("USD");
            value.setCurrencyExponent(2);
            value.setChannelCode("MPGS");
            value.setChannelId(11L);
            value.setChannelMidConfigId(22L);
            value.setChannelTerminalId("MID-TERM-1");
            value.setTransactionDateTime(refundTime);
            value.setTransactionTimeZone("Asia/Shanghai");
            return value;
        }

        private TransactionChannelRequestDO request() {
            TransactionChannelRequestDO value = new TransactionChannelRequestDO();
            value.setRequestId("REQ-1");
            value.setRequestStatus("INIT");
            value.setChannelOrderNo("CHANNEL-ORDER-1");
            value.setChannelTransactionId("CHANNEL-TX-1");
            return value;
        }

        private RefundExecutionMessage message() {
            RefundExecutionMessage value = new RefundExecutionMessage();
            value.setSourceTransactionId("PAY-1");
            value.setSourceTransactionDateTime(sourceTime);
            value.setRootTransactionDateTime(sourceTime);
            return value;
        }
    }
}
