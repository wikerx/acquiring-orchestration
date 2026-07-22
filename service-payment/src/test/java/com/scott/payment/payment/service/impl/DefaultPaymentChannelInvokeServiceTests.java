package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.executor.PaymentChannelExecutor;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentChannelInvokeServiceTests
 * @date : 2026-07-14 21:20
 * @email : scott_x@163.com
 * @description : 收单渠道调用服务测试，验证支付核心上下文转换为渠道统一请求时拆清平台交易 ID、内部生命周期 ID 和渠道交易 ID。
 * @status : create
 */
class DefaultPaymentChannelInvokeServiceTests {

    /**
     * 后续动作调用渠道时，MPGS orderId 使用原平台交易 ID，targetTransactionId 使用原渠道交易 ID。
     */
    @Test
    void shouldUseChannelIdentifiersForFollowUpChannelRequest() {
        PaymentChannelExecutor executor = mock(PaymentChannelExecutor.class);
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelTradeStatus(ChannelTradeStatus.SUCCESS.getCode());
        when(executor.execute(any(ChannelPaymentRequest.class))).thenReturn(response);
        DefaultPaymentChannelInvokeService invokeService = new DefaultPaymentChannelInvokeService(executor);

        PaymentChannelInvokeResultDTO resultDTO = invokeService.invoke(
                followUpCommand(), routeResult(), "OP260714180001", "TX260714180002", "TX260714180001");

        ArgumentCaptor<ChannelPaymentRequest> captor = ArgumentCaptor.forClass(ChannelPaymentRequest.class);
        verify(executor).execute(captor.capture());
        ChannelPaymentRequest request = captor.getValue();
        assertThat(request.getOperationId()).isEqualTo("OP260714180001");
        assertThat(request.getTransactionId()).isEqualTo("TX260714180002");
        assertThat(request.getSourceTransactionId()).isEqualTo("TX260714180001");
        assertThat(request.getChannelOrderNo()).isEqualTo("TX260714180001");
        assertThat(request.getChannelTransactionId()).startsWith("CH");
        assertThat(resultDTO.getRequestId()).startsWith("CR20260712103000000");
        assertThat(resultDTO.getChannelRequest()).isSameAs(request);
        assertThat(resultDTO.getChannelResponse()).isSameAs(response);
        assertThat(request.getExtension()).containsEntry("targetTransactionId", "CH260714180001");
        assertThat(request.getMerchantOrderNo()).isEqualTo("M202607120001");
        assertThat(request.getMerchantOrderId()).isEqualTo("CAP202607120001");
        assertThat(request.getTransactionType()).isEqualTo(PaymentTransactionTypeEnum.CAPTURE.getCode());
    }

    /**
     * 渠道查询勾兑应使用原动作单保存的渠道交易 ID，避免 MPGS RETRIEVE 查询一笔新生成的不存在交易。
     */
    @Test
    void shouldReuseOriginalChannelTransactionIdForQueryRequest() {
        PaymentChannelExecutor executor = mock(PaymentChannelExecutor.class);
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelTradeStatus(ChannelTradeStatus.SUCCESS.getCode());
        when(executor.execute(any(ChannelPaymentRequest.class))).thenReturn(response);
        DefaultPaymentChannelInvokeService invokeService = new DefaultPaymentChannelInvokeService(executor);
        PaymentCreateCommandDTO commandDTO = followUpCommand();
        commandDTO.setTransactionType("QUERY");

        invokeService.invoke(commandDTO, routeResult(), "OP260714180001", "CH260714180001", "TX260714180001");

        ArgumentCaptor<ChannelPaymentRequest> captor = ArgumentCaptor.forClass(ChannelPaymentRequest.class);
        verify(executor).execute(captor.capture());
        ChannelPaymentRequest request = captor.getValue();
        assertThat(request.getTransactionId()).isEqualTo("CH260714180001");
        assertThat(request.getChannelOrderNo()).isEqualTo("TX260714180001");
        assertThat(request.getChannelTransactionId()).isEqualTo("CH260714180001");
        assertThat(request.getTransactionType()).isEqualTo("QUERY");
    }

    private PaymentCreateCommandDTO followUpCommand() {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202607120001");
        commandDTO.setMerchantOrderId("CAP202607120001");
        commandDTO.setTransactionType(PaymentTransactionTypeEnum.CAPTURE.getCode());
        commandDTO.setPaymentMethod("BANK_CARD");
        commandDTO.setAmount(new BigDecimal("5.00"));
        commandDTO.setCurrency("USD");
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 30));
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfoDTO.setSourceTransactionId("TX260714180001");
        transactionInfoDTO.setSourceChannelTransactionId("CH260714180001");
        transactionInfoDTO.setSourceTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 0));
        commandDTO.setTransactionInfo(transactionInfoDTO);
        return commandDTO;
    }

    private PaymentRouteResultDTO routeResult() {
        PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
        routeResultDTO.setChannelId(101L);
        routeResultDTO.setMidConfigId(1001L);
        routeResultDTO.setMidNo("TESTDEVMER031");
        return routeResultDTO;
    }
}
