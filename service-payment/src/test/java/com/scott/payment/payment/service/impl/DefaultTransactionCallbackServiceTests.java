package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelCallbackKind;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.executor.PaymentChannelCallbackExecutor;
import com.scott.payment.channel.payment.registry.PaymentChannelCallbackRegistry;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionChannelCallbackDO;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.mapper.TransactionChannelCallbackLogMapper;
import com.scott.payment.payment.mapper.TransactionChannelCallbackMapper;
import com.scott.payment.payment.mq.TransactionMqConstants;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionCallbackServiceTests
 * @date : 2026-07-14 22:00
 * @email : scott_x@163.com
 * @description : 渠道回调服务单元测试，验证 MPGS 回调按 order.id 与 transaction.id 定位动作并推进平台状态。
 * @status : create
 */
class DefaultTransactionCallbackServiceTests {

    /**
     * ShardingSphere 模式必须在同一交易数据源事务中只使用逻辑表，并以分片时间、版本和当前状态完成回调 CAS。
     */
    @Test
    void shouldUseLogicalCallbackTableFamilyInShardingSphereMode() {
        TransactionChannelCallbackLogMapper callbackLogMapper = mock(TransactionChannelCallbackLogMapper.class);
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 7, 14, 10, 0);
        when(callbackLogMapper.insertLogical(any())).thenReturn(1);
        when(callbackMapper.insertLogical(any())).thenReturn(1);
        when(callbackMapper.updateProcessResultLogical(anyString(), any(), any(), any(), anyString(),
                any(), any(), any(), anyString(), any(), any())).thenReturn(1);
        when(recordService.findOperationByChannelTransaction(
                "202607141000000000001", "CH202607141000000000001")).thenReturn(operation());
        when(recordService.findOrder(transactionDateTime, "OP202607141000000000001")).thenReturn(order());
        when(recordService.completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("AUTHORIZED"), eq("00"), eq("Approved"))).thenReturn(true);
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                callbackLogMapper,
                callbackMapper,
                recordService,
                new CapturingEventOutboxService(),
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedMpgsCallbackHandler()))))),
                new DefaultChannelTransactionStatusResolver());

        TransactionChannelCallbackResultDTO resultDTO = callbackService.recordChannelCallback(callbackCommand());

        assertThat(resultDTO.getCallbackStatus()).isEqualTo("PROCESSED");
        verify(callbackLogMapper).insertLogical(any());
        verify(callbackMapper).selectByIdempotency(
                eq("MPGS"), anyString(), eq(transactionDateTime));
        verify(callbackMapper).insertLogical(any());
        verify(callbackMapper).updateProcessResultLogical(
                anyString(),
                eq(transactionDateTime),
                eq(0),
                eq(List.of("RECEIVED", "FAILED")),
                eq("PROCESSED"),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()),
                eq(PaymentTransactionStatusEnum.PROCESSING.getCode()),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()),
                eq("STATUS_CHANGED"),
                any(),
                any(LocalDateTime.class));
    }

    /**
     * 并发请求在先查后插窗口命中唯一键时，应回查并返回已存在的回调，不得重复推进交易状态。
     */
    @Test
    void shouldRecoverConcurrentDuplicateByReloadingExistingCallback() {
        TransactionChannelCallbackLogMapper callbackLogMapper = mock(TransactionChannelCallbackLogMapper.class);
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 7, 14, 10, 0);
        TransactionChannelCallbackDO existed = existingCallback();
        when(callbackLogMapper.insertLogical(any())).thenReturn(1);
        when(callbackMapper.selectByIdempotency(eq("MPGS"), anyString(), eq(transactionDateTime)))
                .thenReturn(null, existed);
        when(callbackMapper.insertLogical(any()))
                .thenThrow(new DuplicateKeyException("callback idempotency key conflict"));
        when(recordService.findOperationByChannelTransaction(
                "202607141000000000001", "CH202607141000000000001")).thenReturn(operation());
        when(recordService.findOrder(transactionDateTime, "OP202607141000000000001")).thenReturn(order());
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                callbackLogMapper,
                callbackMapper,
                recordService,
                new CapturingEventOutboxService(),
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedMpgsCallbackHandler()))))),
                new DefaultChannelTransactionStatusResolver());

        TransactionChannelCallbackResultDTO resultDTO = callbackService.recordChannelCallback(callbackCommand());

        assertThat(resultDTO.getCallbackId()).isEqualTo("CCB202607141000000000001");
        assertThat(resultDTO.getCallbackStatus()).isEqualTo("PROCESSED");
        assertThat(resultDTO.getProcessResult()).isEqualTo("DUPLICATE");
        verify(callbackMapper, times(2)).selectByIdempotency(eq("MPGS"), anyString(), eq(transactionDateTime));
        verify(recordService, never()).completeByChannelCallback(any(), any(), anyString(),
                anyString(), any(), any(), any(), any(), any());
        verify(callbackMapper, never()).updateProcessResultLogical(
                anyString(), any(), any(), any(), anyString(), any(), any(), any(), anyString(), any(), any());
    }

    /**
     * 回调处理结果 CAS 冲突必须抛出异常，让外层事务回滚交易状态、Outbox 和回调记录。
     */
    @Test
    void shouldFailTransactionWhenLogicalCallbackCasConflicts() {
        TransactionChannelCallbackLogMapper callbackLogMapper = mock(TransactionChannelCallbackLogMapper.class);
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 7, 14, 10, 0);
        when(callbackLogMapper.insertLogical(any())).thenReturn(1);
        when(callbackMapper.insertLogical(any())).thenReturn(1);
        when(callbackMapper.updateProcessResultLogical(anyString(), any(), any(), any(), anyString(),
                any(), any(), any(), anyString(), any(), any())).thenReturn(0);
        when(recordService.findOperationByChannelTransaction(
                "202607141000000000001", "CH202607141000000000001")).thenReturn(operation());
        when(recordService.findOrder(transactionDateTime, "OP202607141000000000001")).thenReturn(order());
        when(recordService.completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("AUTHORIZED"), eq("00"), eq("Approved"))).thenReturn(true);
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                callbackLogMapper,
                callbackMapper,
                recordService,
                new CapturingEventOutboxService(),
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedMpgsCallbackHandler()))))),
                new DefaultChannelTransactionStatusResolver());

        assertThatThrownBy(() -> callbackService.recordChannelCallback(callbackCommand()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("callback process state has changed");
        verify(recordService).completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("AUTHORIZED"), eq("00"), eq("Approved"));
    }

    /**
     * MPGS 成功回调应通过 order.id 和 transaction.id 定位动作单，并调用交易记录服务推进成功终态。
     */
    @Test
    void shouldProcessMpgsApprovedCallbackByChannelOrderAndTransactionId() {
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        when(callbackMapper.insertLogical(any(TransactionChannelCallbackDO.class))).thenReturn(1);
        when(callbackMapper.updateProcessResultLogical(anyString(), any(), any(), any(), anyString(),
                any(), any(), any(), anyString(), any(), any()))
                .thenReturn(1);
        when(recordService.findOperationByChannelTransaction("202607141000000000001", "CH202607141000000000001"))
                .thenReturn(operation());
        when(recordService.findOrder(LocalDateTime.of(2026, 7, 14, 10, 0), "OP202607141000000000001"))
                .thenReturn(order());
        when(recordService.completeByChannelCallback(any(), any(), anyString(), eq(PaymentTransactionStatusEnum.SUCCESS.getCode()),
                any(), any(), eq("AUTHORIZED"), eq("00"), eq("Approved"))).thenReturn(true);
        CapturingEventOutboxService eventOutboxService = new CapturingEventOutboxService();
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                mock(TransactionChannelCallbackLogMapper.class),
                callbackMapper,
                recordService,
                eventOutboxService,
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedMpgsCallbackHandler()))))));

        TransactionChannelCallbackResultDTO resultDTO = callbackService.recordChannelCallback(callbackCommand());

        assertThat(resultDTO.getCallbackStatus()).isEqualTo("PROCESSED");
        assertThat(resultDTO.getProcessResult()).isEqualTo("STATUS_CHANGED");
        assertThat(resultDTO.getTransactionId()).isEqualTo("202607141000000000001");
        verify(recordService).completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("AUTHORIZED"), eq("00"), eq("Approved"));
        assertThat(eventOutboxService.eventDO).isNotNull();
        assertThat(eventOutboxService.eventDO.getEventType())
                .isEqualTo(TransactionMqConstants.TRANSACTION_CALLBACK_PROCESSED_TAG);
        assertThat(eventOutboxService.eventDO.getTransactionId()).isEqualTo("202607141000000000001");
    }

    /**
     * WorldPay 同一订单可能先回调 AUTHORISED 再回调 CAPTURED，幂等键必须区分原始状态，不能吞掉后续 captured 终态事件。
     */
    @Test
    void shouldNotTreatWorldPayCapturedAsDuplicateAfterAuthorisedCallback() {
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        when(callbackMapper.insertLogical(any(TransactionChannelCallbackDO.class))).thenReturn(1);
        when(callbackMapper.updateProcessResultLogical(anyString(), any(), any(), any(), anyString(),
                any(), any(), any(), anyString(), any(), any()))
                .thenReturn(1);
        TransactionOperationDO operationDO = operation();
        operationDO.setTransactionType(PaymentTransactionTypeEnum.PAYMENT.getCode());
        when(recordService.findOperationByChannelTransaction("202607141000000000001", "CH202607141000000000001"))
                .thenReturn(operationDO);
        when(recordService.findOrder(LocalDateTime.of(2026, 7, 14, 10, 0), "OP202607141000000000001"))
                .thenReturn(order());
        when(recordService.completeByChannelCallback(any(), any(), anyString(), eq(PaymentTransactionStatusEnum.SUCCESS.getCode()),
                any(), any(), eq("CAPTURED"), eq("CAPTURED"), eq("CAPTURED"))).thenReturn(true);
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                mock(TransactionChannelCallbackLogMapper.class),
                callbackMapper,
                recordService,
                new CapturingEventOutboxService(),
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedWorldPayCallbackHandler()))))));

        TransactionChannelCallbackCommandDTO commandDTO = callbackCommand();
        commandDTO.setChannelCode("WPGXML");
        commandDTO.setRequestUri("/channel/v1/callbacks/WPGXML");
        commandDTO.setRequestBody("AUTHORISED");
        TransactionChannelCallbackResultDTO authorised = callbackService.recordChannelCallback(commandDTO);
        commandDTO.setRequestBody("CAPTURED");
        TransactionChannelCallbackResultDTO captured = callbackService.recordChannelCallback(commandDTO);

        assertThat(authorised.getCallbackStatus()).isEqualTo("RECEIVED");
        assertThat(authorised.getProcessResult()).isEqualTo("PENDING_STATE_MAPPING");
        assertThat(captured.getCallbackStatus()).isEqualTo("PROCESSED");
        assertThat(captured.getProcessResult()).isEqualTo("STATUS_CHANGED");
        verify(recordService, never()).completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("AUTHORISED"), any(), any());
        verify(recordService).completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("CAPTURED"), eq("CAPTURED"), eq("CAPTURED"));
    }

    /**
     * OpenAPI 入口标记签名非法的渠道回调只能落库排查，支付核心不得推进交易状态或发送商户通知事件。
     */
    @Test
    void shouldRejectUnsafeCallbackWithoutChangingTransactionStatus() {
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        when(callbackMapper.insertLogical(any(TransactionChannelCallbackDO.class))).thenReturn(1);
        when(callbackMapper.updateProcessResultLogical(anyString(), any(), any(), any(), anyString(),
                any(), any(), any(), anyString(), any(), any()))
                .thenReturn(1);
        when(recordService.findOperationByChannelTransaction("202607141000000000001", "CH202607141000000000001"))
                .thenReturn(operation());
        when(recordService.findOrder(LocalDateTime.of(2026, 7, 14, 10, 0), "OP202607141000000000001"))
                .thenReturn(order());
        CapturingEventOutboxService eventOutboxService = new CapturingEventOutboxService();
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                mock(TransactionChannelCallbackLogMapper.class),
                callbackMapper,
                recordService,
                eventOutboxService,
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedMpgsCallbackHandler()))))));
        TransactionChannelCallbackCommandDTO commandDTO = callbackCommand();
        commandDTO.setSignatureValid(false);

        TransactionChannelCallbackResultDTO resultDTO = callbackService.recordChannelCallback(commandDTO);

        assertThat(resultDTO.getCallbackStatus()).isEqualTo("FAILED");
        assertThat(resultDTO.getProcessResult()).isEqualTo("SECURITY_REJECTED");
        assertThat(resultDTO.getFailReason()).isEqualTo("channel callback signature is not valid");
        verify(recordService, never()).completeByChannelCallback(any(), any(), anyString(),
                anyString(), any(), any(), any(), any(), any());
        assertThat(eventOutboxService.eventDO).isNull();
    }

    @Test
    void shouldRecordUnresolvedCallbackWhenOperationIsMissing() {
        TransactionChannelCallbackLogMapper callbackLogMapper = mock(TransactionChannelCallbackLogMapper.class);
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        when(callbackLogMapper.insertLogical(any())).thenReturn(1);
        when(callbackMapper.insertLogical(any())).thenReturn(1);
        when(callbackMapper.updateProcessResultLogical(anyString(), any(), any(), any(), anyString(),
                any(), any(), any(), any(), any(), any())).thenReturn(1);
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                callbackLogMapper,
                callbackMapper,
                recordService,
                new CapturingEventOutboxService(),
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedMpgsCallbackHandler()))))));

        TransactionChannelCallbackResultDTO resultDTO = callbackService.recordChannelCallback(callbackCommand());

        assertThat(resultDTO.getCallbackStatus()).isEqualTo("FAILED");
        assertThat(resultDTO.getFailReason()).isEqualTo("transaction_id can not be resolved from callback");
        verify(recordService, never()).completeByChannelCallback(any(), any(), anyString(),
                anyString(), any(), any(), any(), any(), any());
    }

    /**
     * 3DS Method callback 只是认证前置通知，不得推进支付终态或发送商户通知事件。
     */
    @Test
    void shouldRecordThreeDsCallbackWithoutChangingTransactionStatus() {
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        when(callbackMapper.insertLogical(any(TransactionChannelCallbackDO.class))).thenReturn(1);
        when(callbackMapper.updateProcessResultLogical(anyString(), any(), any(), any(), anyString(),
                any(), any(), any(), anyString(), any(), any()))
                .thenReturn(1);
        when(recordService.findSourceOperationByTransactionId("202607141000000000001"))
                .thenReturn(operation());
        when(recordService.findOrder(LocalDateTime.of(2026, 7, 14, 10, 0), "OP202607141000000000001"))
                .thenReturn(order());
        CapturingEventOutboxService eventOutboxService = new CapturingEventOutboxService();
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                mock(TransactionChannelCallbackLogMapper.class),
                callbackMapper,
                recordService,
                eventOutboxService,
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedMpgsCallbackHandler()))))));
        TransactionChannelCallbackCommandDTO commandDTO = callbackCommand();
        commandDTO.setCallbackType("THREE_DS_AUTHENTICATION_CALLBACK");
        commandDTO.setChannelEventType("THREE_DS_CALLBACK");
        commandDTO.setRequestUri("/channel/v1/callbacks/MPGS/3ds");
        commandDTO.setRequestBody("threeDSServerTransID=7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8"
                + "&threeDSSessionData=encrypted-session-data"
                + "&orderId=202607141000000000001");

        TransactionChannelCallbackResultDTO resultDTO = callbackService.recordChannelCallback(commandDTO);

        assertThat(resultDTO.getCallbackStatus()).isEqualTo("RECEIVED");
        assertThat(resultDTO.getProcessResult()).isEqualTo("PENDING_STATE_MAPPING");
        verify(recordService, never()).completeByChannelCallback(any(), any(), anyString(),
                anyString(), any(), any(), any(), any(), any());
        verify(callbackMapper).updateProcessResultLogical(anyString(), any(), any(), any(),
                eq("RECEIVED"), any(), any(), any(), eq("PENDING_STATE_MAPPING"),
                eq("3ds authentication callback waits payer authentication or payment result confirmation"), any());
        assertThat(eventOutboxService.eventDO).isNull();
    }

    /**
     * MPGS order.notificationUrl 会把后续资金通知投递到原 /3ds URL；Provider 已识别为资金事件时，
     * 支付核心必须忽略入口的旧 3DS 标签并正常推进交易终态。
     */
    @Test
    void shouldProcessFinancialWebhookDeliveredToThreeDsNotificationUrl() {
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        when(callbackMapper.insertLogical(any(TransactionChannelCallbackDO.class))).thenReturn(1);
        when(callbackMapper.updateProcessResultLogical(anyString(), any(), any(), any(), anyString(),
                any(), any(), any(), anyString(), any(), any())).thenReturn(1);
        when(recordService.findOperationByChannelTransaction(
                "202607141000000000001", "CH202607141000000000001")).thenReturn(operation());
        when(recordService.findOrder(LocalDateTime.of(2026, 7, 14, 10, 0), "OP202607141000000000001"))
                .thenReturn(order());
        when(recordService.completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("AUTHORIZED"), eq("00"), eq("Approved"))).thenReturn(true);
        CapturingEventOutboxService eventOutboxService = new CapturingEventOutboxService();
        DefaultTransactionCallbackService callbackService = new DefaultTransactionCallbackService(
                mock(TransactionChannelCallbackLogMapper.class),
                callbackMapper,
                recordService,
                eventOutboxService,
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new FixedMpgsCallbackHandler()))))));
        TransactionChannelCallbackCommandDTO commandDTO = callbackCommand();
        commandDTO.setCallbackType("THREE_DS_AUTHENTICATION_CALLBACK");
        commandDTO.setChannelEventType("THREE_DS_CALLBACK");
        commandDTO.setRequestUri("/channel/v1/callbacks/MPGS/3ds");

        TransactionChannelCallbackResultDTO resultDTO = callbackService.recordChannelCallback(commandDTO);

        assertThat(resultDTO.getCallbackStatus()).isEqualTo("PROCESSED");
        assertThat(resultDTO.getProcessResult()).isEqualTo("STATUS_CHANGED");
        verify(recordService).completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("AUTHORIZED"), eq("00"), eq("Approved"));
        assertThat(eventOutboxService.eventDO).isNotNull();
    }

    private TransactionChannelCallbackCommandDTO callbackCommand() {
        TransactionChannelCallbackCommandDTO commandDTO = new TransactionChannelCallbackCommandDTO();
        commandDTO.setChannelCode("MPGS");
        commandDTO.setRequestUri("/channel/v1/callbacks/MPGS");
        commandDTO.setHttpMethod("POST");
        commandDTO.setSourceIp("127.0.0.1");
        commandDTO.setSignatureValid(true);
        commandDTO.setIpAllowed(true);
        commandDTO.setReceivedTime(LocalDateTime.of(2026, 7, 14, 10, 1));
        commandDTO.setRequestBody("""
                {
                  "result": "SUCCESS",
                  "order": {"id": "202607141000000000001", "status": "AUTHORIZED"},
                  "transaction": {"id": "CH202607141000000000001", "type": "AUTHORIZATION"},
                  "response": {"gatewayCode": "APPROVED", "acquirerCode": "00", "acquirerMessage": "Approved"}
                }
                """);
        return commandDTO;
    }

    private TransactionOperationDO operation() {
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setId(1L);
        operationDO.setOperationId("OP202607141000000000001");
        operationDO.setTransactionId("202607141000000000001");
        operationDO.setChannelOrderNo("202607141000000000001");
        operationDO.setChannelTransactionId("CH202607141000000000001");
        operationDO.setMerchantId("200001");
        operationDO.setMerchantOrderNo("M202607140001");
        operationDO.setMerchantOrderId("AUTH202607140001");
        operationDO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        operationDO.setProcessStage(PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        operationDO.setTransactionCurrency("USD");
        operationDO.setTransactionAmount(new BigDecimal("12.34"));
        operationDO.setTransactionDateTime(LocalDateTime.of(2026, 7, 14, 10, 0));
        operationDO.setVersion(0);
        return operationDO;
    }

    private TransactionOrderDO order() {
        TransactionOrderDO orderDO = new TransactionOrderDO();
        orderDO.setOperationId("OP202607141000000000001");
        orderDO.setRootTransactionId("202607141000000000001");
        orderDO.setLatestTransactionId("202607141000000000001");
        orderDO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        orderDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        orderDO.setTransactionCurrency("USD");
        orderDO.setTransactionAmount(new BigDecimal("12.34"));
        orderDO.setCurrencyExponent(2);
        orderDO.setTransactionDateTime(LocalDateTime.of(2026, 7, 14, 10, 0));
        orderDO.setVersion(0);
        return orderDO;
    }

    private TransactionChannelCallbackDO existingCallback() {
        TransactionChannelCallbackDO callbackDO = new TransactionChannelCallbackDO();
        callbackDO.setCallbackId("CCB202607141000000000001");
        callbackDO.setTransactionId("202607141000000000001");
        callbackDO.setOperationId("OP202607141000000000001");
        callbackDO.setChannelCode("MPGS");
        callbackDO.setChannelOrderNo("202607141000000000001");
        callbackDO.setChannelTransactionId("CH202607141000000000001");
        callbackDO.setCallbackStatus("PROCESSED");
        return callbackDO;
    }

    private static class CapturingEventOutboxService implements TransactionEventOutboxService {

        /**
         * event DO，用于保存 Capturing Event Outbox Service 中与 eventdo 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private TransactionEventOutboxDO eventDO;

        /**
         * 捕获回调处理完成后创建的 Outbox 事件，供用例核对通知事实。
         */
        @Override
        public void save(TransactionEventOutboxDO eventDO) {
            this.eventDO = eventDO;
        }

        /**
         * 返回空集合以隔离回调事件创建测试，不在该替身中触发异步中继。
         */
        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return List.of();
        }

        /**
         * 固定模拟发送状态更新成功；当前用例不验证 Outbox 中继的 CAS 行为。
         */
        @Override
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            return true;
        }

        /**
         * 固定模拟失败状态更新成功；当前用例不验证 Outbox 重试持久化。
         */
        @Override
        public boolean markFailed(TransactionEventOutboxDO eventDO,
                                  LocalDateTime nextRetryTime,
                                  String failReason,
                                  LocalDateTime now) {
            return true;
        }
    }

    /** Provider-neutral SPI fixture; protocol parsing itself is covered by payment-channel-mpgs tests. */
    private static class FixedMpgsCallbackHandler implements PaymentChannelCallbackHandler {

        @Override
        public String channelCode() {
            return "MPGS";
        }

        @Override
        public ChannelCallbackResult handle(ChannelCallbackRequest request) {
            if (request.getRequestUri() != null && request.getRequestUri().endsWith("/3ds")
                    && (request.getBody() == null || !request.getBody().contains("\"type\": \"AUTHORIZATION\""))) {
                return threeDsResult();
            }
            ChannelCallbackResult result = new ChannelCallbackResult();
            result.setChannelCode("MPGS");
            result.setCallbackKind(ChannelCallbackKind.FINANCIAL_TRANSACTION);
            result.setCallbackEventId("CH202607141000000000001");
            result.setChannelOrderNo("202607141000000000001");
            result.setChannelTransactionId("CH202607141000000000001");
            result.setRawChannelStatus("AUTHORIZED");
            result.setChannelTradeStatus(ChannelTradeStatus.AUTHORIZED.getCode());
            result.setSignatureValid(true);
            result.setChannelResponseCode("00");
            result.setChannelResponseMessage("Approved");
            return result;
        }

        private ChannelCallbackResult threeDsResult() {
            ChannelCallbackResult result = new ChannelCallbackResult();
            result.setChannelCode("MPGS");
            result.setCallbackKind(ChannelCallbackKind.THREE_DS_AUTHENTICATION);
            result.setCallbackEventId("7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8");
            result.setChannelOrderNo("202607141000000000001");
            result.setChannelTransactionId("7f880d1d-6d8d-4d7a-83af-7465d3f0c1b8");
            result.setRawChannelStatus("3DS_METHOD_COMPLETED");
            result.setChannelTradeStatus(ChannelTradeStatus.PENDING.getCode());
            result.setSignatureValid(true);
            result.setChannelResponseCode("3DS_METHOD_COMPLETED");
            result.setChannelResponseMessage("3DS method completion callback received");
            return result;
        }
    }

    private static class FixedWorldPayCallbackHandler implements PaymentChannelCallbackHandler {

        /**
         * 提供固定渠道编码，使回调服务能够选择本测试专用的 Worldpay 处理器。
         */
        @Override
        public String channelCode() {
            return "WPGXML";
        }

        /**
         * 将请求体映射为确定性的成功回调结果，便于断言状态推进和 Outbox 事件。
         */
        @Override
        public ChannelCallbackResult handle(ChannelCallbackRequest request) {
            ChannelCallbackResult result = new ChannelCallbackResult();
            result.setChannelCode("WPGXML");
            result.setChannelOrderNo("202607141000000000001");
            result.setChannelTransactionId("CH202607141000000000001");
            result.setRawChannelStatus(request.getBody());
            result.setChannelTradeStatus("CAPTURED".equals(request.getBody())
                    ? ChannelTradeStatus.CAPTURED.getCode()
                    : ChannelTradeStatus.AUTHORIZED.getCode());
            result.setChannelResponseCode(request.getBody());
            result.setChannelResponseMessage(request.getBody());
            return result;
        }
    }
}
