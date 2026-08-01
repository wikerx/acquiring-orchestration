package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.executor.PaymentChannelExecutor;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import com.scott.payment.channel.payment.exception.ChannelTimeoutException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelInvokeService.PaymentChannelInvokeException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
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
@Slf4j
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
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId("CR-ORIGINAL");
        prepared.setChannelOrderNo("ORDER-MPGS-001");
        prepared.setChannelTransactionId("CH-MPGS-001");

        invokeService.invoke(commandDTO, routeResult(), "OP260714180001", "TX260714180099", prepared);

        ArgumentCaptor<ChannelPaymentRequest> captor = ArgumentCaptor.forClass(ChannelPaymentRequest.class);
        verify(executor).execute(captor.capture());
        ChannelPaymentRequest request = captor.getValue();
        assertThat(request.getTransactionId()).isEqualTo("TX260714180099");
        assertThat(request.getChannelOrderNo()).isEqualTo("ORDER-MPGS-001");
        assertThat(request.getChannelTransactionId()).isEqualTo("CH-MPGS-001");
        assertThat(request.getChannelTransactionId()).isNotEqualTo(request.getTransactionId());
        assertThat(request.getTransactionType()).isEqualTo("QUERY");
    }

    /**
     * QUERY 缺少已持久化渠道交易 ID 时，通用调用服务不得把平台 transactionId 回填成 channelTransactionId。
     */
    @Test
    void shouldNotFallbackPlatformTransactionIdAsChannelTransactionIdForQueryRequest() {
        PaymentChannelExecutor executor = mock(PaymentChannelExecutor.class);
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelTradeStatus(ChannelTradeStatus.SUCCESS.getCode());
        when(executor.execute(any(ChannelPaymentRequest.class))).thenReturn(response);
        DefaultPaymentChannelInvokeService invokeService = new DefaultPaymentChannelInvokeService(executor);
        PaymentCreateCommandDTO commandDTO = followUpCommand();
        commandDTO.setTransactionType("QUERY");

        invokeService.invoke(commandDTO, routeResult(), "OP260714180001", "TX260714180099", "ORDER-MPGS-001");

        ArgumentCaptor<ChannelPaymentRequest> captor = ArgumentCaptor.forClass(ChannelPaymentRequest.class);
        verify(executor).execute(captor.capture());
        ChannelPaymentRequest request = captor.getValue();
        assertThat(request.getTransactionId()).isEqualTo("TX260714180099");
        assertThat(request.getChannelOrderNo()).isEqualTo("ORDER-MPGS-001");
        assertThat(request.getChannelTransactionId()).isNull();
    }

    @Test
    void shouldDelegateQueryReferenceSupportToChannelExecutor() {
        PaymentChannelExecutor executor = mock(PaymentChannelExecutor.class);
        when(executor.supportsQueryReference(any(ChannelPaymentRequest.class))).thenReturn(true);
        DefaultPaymentChannelInvokeService invokeService = new DefaultPaymentChannelInvokeService(executor);
        PaymentCreateCommandDTO commandDTO = followUpCommand();
        commandDTO.setTransactionType("QUERY");
        PaymentPreparedChannelRequestDTO prepared = new PaymentPreparedChannelRequestDTO();
        prepared.setRequestId("CR-ORIGINAL");
        prepared.setChannelOrderNo("ORDER-MPGS-001");
        prepared.setChannelTransactionId("CH-MPGS-001");

        boolean supported = invokeService.supportsQueryReference(
                commandDTO, routeResult(), "OP260714180001", "TX260714180099", prepared);

        assertThat(supported).isTrue();
        ArgumentCaptor<ChannelPaymentRequest> captor = ArgumentCaptor.forClass(ChannelPaymentRequest.class);
        verify(executor).supportsQueryReference(captor.capture());
        assertThat(captor.getValue().getTransactionId()).isEqualTo("TX260714180099");
        assertThat(captor.getValue().getChannelOrderNo()).isEqualTo("ORDER-MPGS-001");
        assertThat(captor.getValue().getChannelTransactionId()).isEqualTo("CH-MPGS-001");
        assertThat(captor.getValue().getExtension()).containsEntry("requestId", "CR-ORIGINAL");
    }

    /**
     * 渠道请求必须优先透传数据库币种表解析出的辅币位，避免 Worldpay JSON 默认按两位小数换算金额。
     */
    @Test
    void shouldPropagateDatabaseCurrencyExponentToChannelRequest() {
        PaymentChannelExecutor executor = mock(PaymentChannelExecutor.class);
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelTradeStatus(ChannelTradeStatus.SUCCESS.getCode());
        when(executor.execute(any(ChannelPaymentRequest.class))).thenReturn(response);
        IsoDictionaryService isoDictionaryService = mock(IsoDictionaryService.class);
        when(isoDictionaryService.getCurrency("USD")).thenReturn(Optional.of(new IsoCurrencyInfo(
                "USD", "840", "US Dollar", "美元", 3, 1000, new BigDecimal("0.001"), "$")));
        DefaultPaymentChannelInvokeService invokeService = new DefaultPaymentChannelInvokeService(executor, isoDictionaryService);

        invokeService.invoke(followUpCommand(), routeResult(), "OP260714180001", "TX260714180002", "TX260714180001");

        ArgumentCaptor<ChannelPaymentRequest> captor = ArgumentCaptor.forClass(ChannelPaymentRequest.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().getExtension()).containsEntry("currencyExponent", "3");
    }

    /**
     * 渠道请求超时后无法确认资金动作是否已被受理，调用结果必须显式标记为不确定。
     */
    @Test
    void shouldMarkChannelTimeoutOutcomeUncertain() {
        log.info("测试渠道超时分类：模拟渠道执行器抛出超时异常，预期保留 TIMEOUT 和结果不确定标志");
        PaymentChannelExecutor executor = mock(PaymentChannelExecutor.class);
        when(executor.execute(any(ChannelPaymentRequest.class)))
                .thenThrow(new ChannelTimeoutException("simulated timeout"));
        DefaultPaymentChannelInvokeService invokeService = new DefaultPaymentChannelInvokeService(executor);

        PaymentChannelInvokeException exception = catchThrowableOfType(
                PaymentChannelInvokeException.class,
                () -> invokeService.invoke(followUpCommand(), routeResult(),
                        "OP260714180001", "TX260714180002", "TX260714180001"));

        assertThat(exception.getInvokeResult().getRequestStatus()).isEqualTo("TIMEOUT");
        assertThat(exception.getInvokeResult().isOutcomeUncertain()).isTrue();
        log.info("渠道超时分类验证完成：requestStatus=TIMEOUT，outcomeUncertain=true");
    }

    /**
     * 请求发送前的本地配置失败可确定渠道未受理，调用结果不得进入待勾兑状态。
     */
    @Test
    void shouldKeepPreDispatchRequestFailureOutcomeCertain() {
        log.info("测试发送前失败分类：模拟渠道必填配置缺失，预期 requestStatus=FAILED 且结果确定");
        PaymentChannelExecutor executor = mock(PaymentChannelExecutor.class);
        when(executor.execute(any(ChannelPaymentRequest.class)))
                .thenThrow(new ChannelRequestException("MPGS baseUrl is required"));
        DefaultPaymentChannelInvokeService invokeService = new DefaultPaymentChannelInvokeService(executor);

        PaymentChannelInvokeException exception = catchThrowableOfType(
                PaymentChannelInvokeException.class,
                () -> invokeService.invoke(followUpCommand(), routeResult(),
                        "OP260714180001", "TX260714180002", "TX260714180001"));

        assertThat(exception.getInvokeResult().getRequestStatus()).isEqualTo("FAILED");
        assertThat(exception.getInvokeResult().isOutcomeUncertain()).isFalse();
        log.info("发送前失败分类验证完成：requestStatus=FAILED，outcomeUncertain=false");
    }

    /**
     * 请求发出后响应缺失或无法解析时不能确定资金结果，必须等待查询或回调勾兑。
     */
    @Test
    void shouldMarkInvalidChannelResponseOutcomeUncertain() {
        log.info("测试渠道响应异常分类：模拟响应体不可解析，预期 requestStatus=FAILED 且结果不确定");
        PaymentChannelExecutor executor = mock(PaymentChannelExecutor.class);
        when(executor.execute(any(ChannelPaymentRequest.class)))
                .thenThrow(new ChannelResponseException("MPGS response body is empty"));
        DefaultPaymentChannelInvokeService invokeService = new DefaultPaymentChannelInvokeService(executor);

        PaymentChannelInvokeException exception = catchThrowableOfType(
                PaymentChannelInvokeException.class,
                () -> invokeService.invoke(followUpCommand(), routeResult(),
                        "OP260714180001", "TX260714180002", "TX260714180001"));

        assertThat(exception.getInvokeResult().getRequestStatus()).isEqualTo("FAILED");
        assertThat(exception.getInvokeResult().isOutcomeUncertain()).isTrue();
        log.info("渠道响应异常分类验证完成：requestStatus=FAILED，outcomeUncertain=true");
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
