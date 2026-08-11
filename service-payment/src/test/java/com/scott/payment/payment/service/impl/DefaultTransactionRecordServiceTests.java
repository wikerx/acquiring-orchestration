package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentFailureReasonEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.entity.TransactionAmountChangeLogDO;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionChannelInteractionLogDO;
import com.scott.payment.payment.entity.TransactionMerchantApiInteractionLogDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionPaymentMethodInfoDO;
import com.scott.payment.payment.entity.TransactionStatusHistoryDO;
import com.scott.payment.payment.entity.TransactionFlowEventDO;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import com.scott.payment.payment.mapper.TransactionOrderMapper;
import com.scott.payment.payment.mapper.TransactionStatusHistoryMapper;
import com.scott.payment.payment.mapper.TransactionChannelRequestMapper;
import com.scott.payment.payment.mapper.TransactionChannelInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionFlowEventMapper;
import com.scott.payment.payment.mapper.TransactionAmountChangeLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantApiInteractionLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.mapper.TransactionPaymentMethodInfoMapper;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionRecordServiceTests
 * @date : 2026-07-14 18:10
 * @email : scott_x@163.com
 * @description : 交易事实记录服务单元测试，验证逻辑表写入携带 transaction_date_time，并保护金额、状态和通知语义。
 * @status : create
 */
class DefaultTransactionRecordServiceTests {

    @Test
    void merchantApiResponseLogShouldUseExplicitTransactionDateTime() {
        TransactionMerchantApiInteractionLogMapper merchantApiLogMapper =
                mock(TransactionMerchantApiInteractionLogMapper.class);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 7, 14, 12, 30, 45, 123_000_000);
        when(merchantApiLogMapper.updateResponseCipherLogical(
                eq("opaque-transaction-id"),
                eq(transactionDateTime),
                eq("request-a"),
                any(),
                eq("digest-a"),
                eq("cipher-mask-a"),
                any(LocalDateTime.class)))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                mock(TransactionOrderMapper.class),
                mock(TransactionOperationMapper.class),
                mock(TransactionStatusHistoryMapper.class),
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                merchantApiLogMapper,
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        TransactionMerchantApiResponseLogUpdateCommandDTO command =
                new TransactionMerchantApiResponseLogUpdateCommandDTO();
        command.setTransactionId("opaque-transaction-id");
        command.setTransactionDateTime(transactionDateTime);
        command.setRequestId("request-a");
        command.setResponseCipherDigest("digest-a");
        command.setResponseCipherMasked("cipher-mask-a");

        assertThat(recordService.updateMerchantApiResponseLog(command)).isTrue();

        verify(merchantApiLogMapper).updateResponseCipherLogical(
                eq("opaque-transaction-id"),
                eq(transactionDateTime),
                eq("request-a"),
                any(),
                eq("digest-a"),
                eq("cipher-mask-a"),
                any(LocalDateTime.class));
    }

    /**
     * 首次交易事实应写入逻辑表，并保留业务时区与 UTC 时间字段供 ShardingSphere 路由。
     */
    @Test
    void shouldRecordInitialTransactionThroughLogicalTablesWithShardTime() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionPaymentMethodInfoMapper paymentMethodInfoMapper = mock(TransactionPaymentMethodInfoMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        Captured<TransactionOrderDO> orderCapture = new Captured<>();
        Captured<TransactionOperationDO> operationCapture = new Captured<>();
        Captured<TransactionStatusHistoryDO> historyCapture = new Captured<>();
        Captured<TransactionPaymentMethodInfoDO> paymentInfoCapture = new Captured<>();
        when(orderMapper.insert(any(TransactionOrderDO.class))).thenAnswer(invocation -> {
            orderCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(operationMapper.insert(any(TransactionOperationDO.class))).thenAnswer(invocation -> {
            operationCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenAnswer(invocation -> {
            historyCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(paymentMethodInfoMapper.insertLogical(any(TransactionPaymentMethodInfoDO.class))).thenAnswer(invocation -> {
            paymentInfoCapture.value = invocation.getArgument(0);
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                paymentMethodInfoMapper,
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        recordService.recordInitialTransaction(baseCommand(), routeResult(), channelInvokeResult(), resultDTO(),
                PaymentRiskDecisionEnum.PASS, 2);

        assertThat(orderCapture.value.getOperationId()).isEqualTo("OP260714180001");
        assertThat(orderCapture.value.getRootTransactionId()).isEqualTo("TX260714180001");
        assertThat(orderCapture.value.getChannelOrderNo()).isEqualTo("CODX260714180001");
        assertThat(orderCapture.value.getTransactionUtcTime()).isEqualTo(LocalDateTime.of(2026, 6, 30, 16, 30));
        assertThat(orderCapture.value.getAuthorizedAmount()).isEqualByComparingTo("12.34");
        assertThat(orderCapture.value.getAvailableCaptureAmount()).isEqualByComparingTo("12.34");
        assertThat(orderCapture.value.getMerchantWebsite()).isEqualTo("https://merchant.example.com/checkout");
        assertThat(operationCapture.value.getChannelOrderNo()).isEqualTo("CODX260714180001");
        assertThat(operationCapture.value.getChannelTransactionId()).isEqualTo("CH260714180001");
        assertThat(operationCapture.value.getChannelResponseCode()).isEqualTo("00");
        assertThat(operationCapture.value.getAuthCode()).isEqualTo("123456");
        assertThat(operationCapture.value.getRrn()).isEqualTo("RCPT001");
        assertThat(operationCapture.value.getAcquirerReferenceNo()).isEqualTo("REF001");
        assertThat(operationCapture.value.getApprovedAmount()).isEqualByComparingTo("12.34");
        assertThat(operationCapture.value.getMerchantOperationNo()).isEqualTo("M202607140001");
        assertThat(operationCapture.value.getRequestSource()).isEqualTo("HOSTED_CHECKOUT");
        assertThat(paymentInfoCapture.value.getPaymentMethod()).isEqualTo("BANK_CARD");
        assertThat(paymentInfoCapture.value.getPaymentBrand()).isEqualTo("MASTERCARD");
        assertThat(paymentInfoCapture.value.getIssuerCountry()).isEqualTo("AE");
        assertThat(paymentInfoCapture.value.getCardBin()).isEqualTo("51234567");
        assertThat(paymentInfoCapture.value.getCardLast4()).isEqualTo("0008");
        assertThat(paymentInfoCapture.value.getCardNumberMasked()).doesNotContain("5123456789010008");
        assertThat(paymentInfoCapture.value.getPaymentAccountHash()).isNotBlank();
        verify(historyMapper, times(2)).insertLogical(any(TransactionStatusHistoryDO.class));
    }

    /**
     * ShardingSphere 单写模式应将首次交易全表族交给逻辑 Mapper，禁止回退到动态物理表 SQL。
     */
    @Test
    void shouldRecordInitialTransactionTableFamilyThroughLogicalMappers() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionChannelRequestMapper channelRequestMapper = mock(TransactionChannelRequestMapper.class);
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        TransactionMerchantApiInteractionLogMapper merchantApiLogMapper = mock(TransactionMerchantApiInteractionLogMapper.class);
        TransactionPaymentMethodInfoMapper paymentMethodInfoMapper = mock(TransactionPaymentMethodInfoMapper.class);
        TransactionShardingProperties properties = logicalShardingProperties();
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                interactionLogMapper,
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                notificationMapper,
                merchantApiLogMapper,
                paymentMethodInfoMapper,
                new TransactionShardingKeyParser(),
                properties);
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setMerchantRequestPlainJsonMasked("{\"orderNo\":\"M202607140001\"}");
        commandDTO.setCallbackUrl("https://merchant.example/callback");

        recordService.recordInitialTransaction(
                commandDTO, routeResult(), channelInvokeResult(), resultDTO(), PaymentRiskDecisionEnum.PASS, 2);

        verify(orderMapper).insert(any(TransactionOrderDO.class));
        verify(operationMapper).insert(any(TransactionOperationDO.class));
        verify(historyMapper, times(2)).insertLogical(any(TransactionStatusHistoryDO.class));
        verify(channelRequestMapper).insertLogical(any(TransactionChannelRequestDO.class));
        verify(interactionLogMapper).insertLogical(any(TransactionChannelInteractionLogDO.class));
        verify(flowEventMapper, times(5)).insertLogical(any(TransactionFlowEventDO.class));
        verify(paymentMethodInfoMapper).insertLogical(any(TransactionPaymentMethodInfoDO.class));
        verify(merchantApiLogMapper).insertLogical(any(TransactionMerchantApiInteractionLogDO.class));
        verify(notificationMapper).insertLogical(any(TransactionMerchantNotificationDO.class));
    }

    /**
     * 锁查询必须携带精确交易分片时间，确保 FOR UPDATE 只路由到一个季度节点。
     */
    @Test
    void shouldLockOrderThroughSingleLogicalShard() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 7, 1, 0, 30);
        TransactionOrderDO orderDO = processingInitialOrder();
        when(orderMapper.selectByOperationIdForUpdate("OP202607010030000000001", transactionDateTime))
                .thenReturn(orderDO);
        TransactionShardingProperties properties = logicalShardingProperties();
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                mock(TransactionOperationMapper.class),
                mock(TransactionStatusHistoryMapper.class),
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                properties);

        TransactionOrderDO locked = recordService.lockOrder(
                transactionDateTime, "OP202607010030000000001");

        assertThat(locked).isSameAs(orderDO);
        verify(orderMapper).selectByOperationIdForUpdate("OP202607010030000000001", transactionDateTime);
    }

    /**
     * 回调终态推进必须在逻辑表上同时保护动作版本、主单版本和通知初始版本。
     */
    @Test
    void shouldCompleteCallbackWithLogicalOperationOrderAndNotificationCas() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 7, 1, 0, 30);
        when(operationMapper.completeStatus(any(), any(), any(), anyString(), anyString(), any(), any(),
                any(), any(), any(), any(), any(), any(), anyString())).thenReturn(1);
        when(orderMapper.markInitialSuccess(anyString(), any(), anyString(), any(BigDecimal.class), any(), anyString()))
                .thenReturn(1);
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenReturn(1);
        when(flowEventMapper.insertLogical(any(TransactionFlowEventDO.class))).thenReturn(1);
        when(notificationMapper.activateByTransactionId(
                anyString(), any(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ArgumentCaptor<String> callbackPayloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> notificationAuditPayloadCaptor = ArgumentCaptor.forClass(String.class);
        TransactionShardingProperties properties = logicalShardingProperties();
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                notificationMapper,
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                properties);

        TransactionOperationDO operationDO = processingInitialOperation();
        operationDO.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
        boolean changed = recordService.completeByChannelCallback(
                operationDO,
                processingInitialOrder(),
                "CCB202607010030000000001",
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                null,
                null,
                "AUTHORIZED",
                "00",
                "Approved");

        assertThat(changed).isTrue();
        verify(operationMapper).completeStatus(
                11L,
                transactionDateTime,
                0,
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                PaymentProcessStageEnum.FINISHED.getCode(),
                null,
                null,
                "AUTHORIZED",
                "00",
                "Approved",
                null,
                null,
                null,
                "MATCHED");
        verify(orderMapper).markInitialSuccess(
                "OP202607010030000000001",
                transactionDateTime,
                "TX202607010030000000001",
                new BigDecimal("12.34"),
                0,
                "MATCHED");
        verify(notificationMapper).activateByTransactionId(
                eq("TX202607010030000000001"),
                eq(transactionDateTime),
                eq(0),
                callbackPayloadCaptor.capture(),
                notificationAuditPayloadCaptor.capture(),
                any(LocalDateTime.class),
                any(LocalDateTime.class));
        assertThat(callbackPayloadCaptor.getValue())
                .contains("\"transactionStatus\":\"SUCCESS\"")
                .contains("\"processStage\":\"FINISHED\"")
                .doesNotContain("\"processStage\":\"CHANNEL_REQUESTING\"");
        assertThat(notificationAuditPayloadCaptor.getValue())
                .contains("\"transactionStatus\":\"SUCCESS\"");
    }

    /**
     * 渠道 HTTP 调用完成但渠道业务返回失败时，流程事件必须按业务失败落库，避免后台时间轴显示绿色成功。
     */
    @Test
    void shouldMarkChannelFlowEventFailedWhenChannelBusinessResultFailed() {
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        CapturedList<TransactionFlowEventDO> flowEventCapture = new CapturedList<>();
        when(flowEventMapper.insertLogical(any(TransactionFlowEventDO.class))).thenAnswer(invocation -> {
            flowEventCapture.values.add(invocation.getArgument(0));
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                mock(TransactionOrderMapper.class),
                mock(TransactionOperationMapper.class),
                mock(TransactionStatusHistoryMapper.class),
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        PaymentChannelInvokeResultDTO invokeResultDTO = channelInvokeResult();
        invokeResultDTO.getChannelResponse().setRawChannelStatus("ERROR");
        invokeResultDTO.getChannelResponse().setChannelTradeStatus("FAILED");
        invokeResultDTO.getChannelResponse().setChannelResponseCode("INVALID_REQUEST");
        invokeResultDTO.getChannelResponse().setChannelResponseMessage("Unexpected parameter 'authentication.threeDs.acsEci'");
        PaymentCreateResultDTO resultDTO = resultDTO();
        resultDTO.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        resultDTO.setFailReasonCode("CHANNEL_REQUEST_FAILED");
        resultDTO.setFailReasonMessage("Channel request failed");

        recordService.recordInitialTransaction(baseCommand(), routeResult(), invokeResultDTO, resultDTO,
                PaymentRiskDecisionEnum.PASS, 2);

        TransactionFlowEventDO channelEvent = flowEventCapture.values.stream()
                .filter(event -> "CHANNEL_CALLED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        TransactionFlowEventDO statusEvent = flowEventCapture.values.stream()
                .filter(event -> "STATUS_RECORDED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(channelEvent.getEventStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(channelEvent.getEventContent()).contains("渠道交易状态：FAILED");
        assertThat(channelEvent.getErrorMessage()).contains("Unexpected parameter");
        assertThat(statusEvent.getEventStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(statusEvent.getEventName()).isEqualTo("交易失败");
        assertThat(statusEvent.getEventContent())
                .isEqualTo(ApiResultEnum.PAYMENT_REJECTED.getCode() + "：" + ApiResultEnum.PAYMENT_REJECTED.getMessage());
        assertThat(statusEvent.getErrorCode()).isEqualTo(ApiResultEnum.PAYMENT_REJECTED.getCode());
        assertThat(statusEvent.getErrorCode()).isNotEqualTo(resultDTO.getFailReasonCode());
    }

    /**
     * 风控拒绝属于交易失败，应写入交易列表事实和流程时间轴，并保留风控记录号供后台详情关联。
     */
    @Test
    void shouldRecordRiskBlockedInitialTransactionAsFailedWithRiskTimeline() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        Captured<TransactionOrderDO> orderCapture = new Captured<>();
        Captured<TransactionOperationDO> operationCapture = new Captured<>();
        CapturedList<TransactionFlowEventDO> flowEventCapture = new CapturedList<>();
        when(orderMapper.insert(any(TransactionOrderDO.class))).thenAnswer(invocation -> {
            orderCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(operationMapper.insert(any(TransactionOperationDO.class))).thenAnswer(invocation -> {
            operationCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenReturn(1);
        when(flowEventMapper.insertLogical(any(TransactionFlowEventDO.class))).thenAnswer(invocation -> {
            flowEventCapture.values.add(invocation.getArgument(0));
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setRiskRecordNo("RK202607280001");
        commandDTO.setRiskCode("AML_HIT");
        commandDTO.setRiskMessage("aml rule is hit");
        PaymentCreateResultDTO resultDTO = resultDTO();
        resultDTO.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        resultDTO.setFailReasonCode(PaymentFailureReasonEnum.RISK_REJECTED.getCode());
        resultDTO.setFailReasonMessage(PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE);
        resultDTO.setMerchantResponseCode(ApiResultEnum.PAYMENT_REJECTED.getCode());
        resultDTO.setMerchantResponseMessage(PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE);

        recordService.recordInitialTransaction(commandDTO, null, null, resultDTO, PaymentRiskDecisionEnum.REJECT, 2);

        TransactionFlowEventDO riskEvent = flowEventCapture.values.stream()
                .filter(event -> "RISK_CHECKED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        TransactionFlowEventDO statusEvent = flowEventCapture.values.stream()
                .filter(event -> "STATUS_RECORDED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(orderCapture.value.getTransactionStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(orderCapture.value.getFailReasonMessage()).isEqualTo(PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE);
        assertThat(orderCapture.value.getMerchantVisibleMessage()).isEqualTo(PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE);
        assertThat(orderCapture.value.getInternalRiskRecordNo()).isEqualTo("RK202607280001");
        assertThat(operationCapture.value.getFailReasonMessage()).isEqualTo(PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE);
        assertThat(riskEvent.getEventStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(riskEvent.getEventContent()).contains("REJECT").contains("RK202607280001").contains("AML_HIT");
        assertThat(riskEvent.getErrorCode()).isEqualTo(PaymentFailureReasonEnum.RISK_REJECTED.getCode());
        assertThat(statusEvent.getEventName()).isEqualTo("交易失败");
        assertThat(statusEvent.getEventContent()).isEqualTo("F210：Risk blocked");
        assertThat(statusEvent.getErrorCode()).isEqualTo("F210");
        assertThat(statusEvent.getErrorMessage()).isEqualTo("Risk blocked");
        assertThat(statusEvent.getErrorCode()).isNotEqualTo(PaymentFailureReasonEnum.RISK_REJECTED.getCode());
        assertThat(flowEventCapture.values)
                .noneMatch(event -> "ROUTE_SELECTED".equals(event.getEventType()))
                .noneMatch(event -> "CHANNEL_CALLED".equals(event.getEventType()));
        verify(historyMapper, times(2)).insertLogical(any(TransactionStatusHistoryDO.class));
    }

    /**
     * 后续交易动作应按本次动作时间写入动作和日志分表，原生命周期主单仍按根交易时间 CAS 更新。
     */
    @Test
    void shouldRecordFollowUpOperationByCurrentTransactionTimeAndUpdateSourceOrderByRootTime() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionChannelRequestMapper channelRequestMapper = mock(TransactionChannelRequestMapper.class);
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        TransactionAmountChangeLogMapper amountChangeLogMapper = mock(TransactionAmountChangeLogMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        TransactionPaymentMethodInfoMapper paymentMethodInfoMapper = mock(TransactionPaymentMethodInfoMapper.class);
        Captured<TransactionOperationDO> operationCapture = new Captured<>();
        Captured<TransactionStatusHistoryDO> historyCapture = new Captured<>();
        when(operationMapper.countByOperationId(anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(operationMapper.insert(any(TransactionOperationDO.class))).thenAnswer(invocation -> {
            operationCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenAnswer(invocation -> {
            historyCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(orderMapper.increaseCapturedAmount(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any()))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                interactionLogMapper,
                flowEventMapper,
                amountChangeLogMapper,
                notificationMapper,
                mock(TransactionMerchantApiInteractionLogMapper.class),
                paymentMethodInfoMapper,
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        recordService.recordFollowUpTransaction(followUpRecord());

        assertThat(operationCapture.value.getTransactionId()).isEqualTo("TX202610011000000000001");
        assertThat(operationCapture.value.getTransactionDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        assertThat(operationCapture.value.getSourceOperationId()).isEqualTo("OP202607010030000000001");
        assertThat(operationCapture.value.getMerchantOperationNo()).isEqualTo("CAPTURE202610010001");
        verify(orderMapper).increaseCapturedAmount(
                "OP202607010030000000001",
                LocalDateTime.of(2026, 7, 1, 0, 30),
                "TX202610011000000000001",
                new BigDecimal("5.00"),
                0);
    }

    /**
     * 后续交易不适用内风控，流程事件必须记录 SKIP，不能伪造 PASS 或继承外部误传决策。
     */
    @Test
    void shouldRecordSkipRiskDecisionForFollowUpFlowEvent() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        CapturedList<TransactionFlowEventDO> flowEventCapture = new CapturedList<>();
        when(operationMapper.countByOperationId(anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(operationMapper.insert(any(TransactionOperationDO.class))).thenReturn(1);
        when(orderMapper.increaseCapturedAmount(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any()))
                .thenReturn(1);
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenReturn(1);
        when(flowEventMapper.insertLogical(any(TransactionFlowEventDO.class))).thenAnswer(invocation -> {
            flowEventCapture.values.add(invocation.getArgument(0));
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        TransactionFollowUpRecordDTO recordDTO = followUpRecord();
        recordDTO.setRiskDecisionEnum(PaymentRiskDecisionEnum.REVIEW);

        recordService.recordFollowUpTransaction(recordDTO);

        TransactionFlowEventDO riskEvent = flowEventCapture.values.stream()
                .filter(event -> "RISK_CHECKED".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(riskEvent.getEventStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(riskEvent.getEventContent()).contains("SKIP").contains("不适用");
        assertThat(flowEventCapture.values)
                .anyMatch(event -> "ROUTE_SELECTED".equals(event.getEventType()))
                .anyMatch(event -> "CHANNEL_CALLED".equals(event.getEventType()));
    }

    /**
     * 商户后台发起退款时不接触原始回调地址，Payment 必须按源交易精确分片时间继承通知配置。
     */
    @Test
    void shouldInheritSourceTransactionCallbackWhenRecordingRefund() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        Captured<TransactionMerchantNotificationDO> notificationCapture = new Captured<>();
        LocalDateTime sourceTransactionDateTime = LocalDateTime.of(2026, 7, 1, 0, 30);
        TransactionMerchantNotificationDO sourceNotification = new TransactionMerchantNotificationDO();
        sourceNotification.setNotifyConfigSnapshotJson(
                "{\"callbackUrl\":\"https://merchant.example/refund-callback?source=qa\"}");
        when(operationMapper.countByOperationId(anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(operationMapper.insert(any(TransactionOperationDO.class))).thenReturn(1);
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenReturn(1);
        when(orderMapper.increaseRefundedAmount(anyString(), any(LocalDateTime.class), anyString(),
                any(BigDecimal.class), any())).thenReturn(1);
        when(notificationMapper.selectLatestConfigByTransactionId(
                "200001", "TX202607010030000000001", sourceTransactionDateTime))
                .thenReturn(sourceNotification);
        when(notificationMapper.insertLogical(any(TransactionMerchantNotificationDO.class))).thenAnswer(invocation -> {
            notificationCapture.value = invocation.getArgument(0);
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                notificationMapper,
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        TransactionFollowUpRecordDTO recordDTO = followUpRecord();
        recordDTO.getSourceOrderDO().setCapturedAmount(new BigDecimal("12.34"));
        recordDTO.getSourceOrderDO().setAvailableRefundAmount(new BigDecimal("12.34"));
        recordDTO.getCommandDTO().setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
        recordDTO.getCommandDTO().getTransactionInfo().setSourceTransactionDateTime(sourceTransactionDateTime);
        recordDTO.getResultDTO().setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());

        recordService.recordFollowUpTransaction(recordDTO);

        assertThat(notificationCapture.value).isNotNull();
        assertThat(notificationCapture.value.getNotifyConfigSnapshotJson())
                .contains("https://merchant.example/refund-callback?source=qa");
        assertThat(notificationCapture.value.getTargetUrlMasked())
                .isEqualTo("https://merchant.example/refund-callback?***");
        verify(notificationMapper).selectLatestConfigByTransactionId(
                "200001", "TX202607010030000000001", sourceTransactionDateTime);
    }

    /**
     * 源通知快照损坏时不得回滚已经落库的退款动作，也不得创建缺少有效地址的通知任务。
     */
    @Test
    void shouldKeepRefundFactsWhenSourceNotificationConfigIsInvalid() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        LocalDateTime sourceTransactionDateTime = LocalDateTime.of(2026, 7, 1, 0, 30);
        TransactionMerchantNotificationDO sourceNotification = new TransactionMerchantNotificationDO();
        sourceNotification.setNotifyConfigSnapshotJson("{invalid-json");
        when(operationMapper.countByOperationId(anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(operationMapper.insert(any(TransactionOperationDO.class))).thenReturn(1);
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenReturn(1);
        when(orderMapper.increaseRefundedAmount(anyString(), any(LocalDateTime.class), anyString(),
                any(BigDecimal.class), any())).thenReturn(1);
        when(notificationMapper.selectLatestConfigByTransactionId(
                "200001", "TX202607010030000000001", sourceTransactionDateTime))
                .thenReturn(sourceNotification);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                notificationMapper,
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        TransactionFollowUpRecordDTO recordDTO = followUpRecord();
        recordDTO.getSourceOrderDO().setCapturedAmount(new BigDecimal("12.34"));
        recordDTO.getSourceOrderDO().setAvailableRefundAmount(new BigDecimal("12.34"));
        recordDTO.getCommandDTO().setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
        recordDTO.getCommandDTO().getTransactionInfo().setSourceTransactionDateTime(sourceTransactionDateTime);
        recordDTO.getResultDTO().setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());

        recordService.recordFollowUpTransaction(recordDTO);

        verify(operationMapper).insert(any(TransactionOperationDO.class));
        verify(notificationMapper, never()).insertLogical(any(TransactionMerchantNotificationDO.class));
    }

    /**
     * 授权撤销成功后，主单可请款金额必须被清零，避免页面和后续请款校验继续显示可用额度。
     */
    @Test
    void shouldClearAvailableCaptureAmountWhenVoidSucceeds() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        when(operationMapper.countByOperationId(anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(operationMapper.insert(any(TransactionOperationDO.class))).thenReturn(1);
        when(orderMapper.markVoidSuccess(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any()))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        TransactionFollowUpRecordDTO recordDTO = followUpRecord();
        recordDTO.getCommandDTO().setTransactionType(PaymentTransactionTypeEnum.VOID.getCode());
        recordDTO.getCommandDTO().setAmount(null);
        recordDTO.getResultDTO().setTransactionType(PaymentTransactionTypeEnum.VOID.getCode());
        recordDTO.getResultDTO().setTransactionId("TX202610011000000000002");
        recordDTO.getResultDTO().setAmount(1234L);

        recordService.recordFollowUpTransaction(recordDTO);

        verify(orderMapper).markVoidSuccess(
                "OP202607010030000000001",
                LocalDateTime.of(2026, 7, 1, 0, 30),
                "TX202610011000000000002",
                new BigDecimal("12.34"),
                0);
    }

    /**
     * 增量授权成功后应累计原授权生命周期主单金额，同时保留本次增量金额变动日志。
     */
    @Test
    void shouldIncreaseAuthorizationLifecycleAmountWhenIncrementalAuthorizationSucceeds() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionAmountChangeLogMapper amountChangeLogMapper = mock(TransactionAmountChangeLogMapper.class);
        Captured<TransactionAmountChangeLogDO> amountChangeCapture = new Captured<>();
        when(operationMapper.countByOperationId(anyString(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(operationMapper.insert(any(TransactionOperationDO.class))).thenReturn(1);
        when(orderMapper.increaseAuthorizedAmount(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any()))
                .thenReturn(1);
        when(amountChangeLogMapper.insertLogical(any(TransactionAmountChangeLogDO.class))).thenAnswer(invocation -> {
            amountChangeCapture.value = invocation.getArgument(0);
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                amountChangeLogMapper,
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        TransactionFollowUpRecordDTO recordDTO = followUpRecord();
        recordDTO.getCommandDTO().setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        recordDTO.getCommandDTO().setAmount(new BigDecimal("20.00"));
        recordDTO.getResultDTO().setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        recordDTO.getResultDTO().setTransactionId("TX202610011000000000003");
        recordDTO.getResultDTO().setAmount(2000L);

        recordService.recordFollowUpTransaction(recordDTO);

        verify(orderMapper).increaseAuthorizedAmount(
                "OP202607010030000000001",
                LocalDateTime.of(2026, 7, 1, 0, 30),
                "TX202610011000000000003",
                new BigDecimal("20.00"),
                0);
        assertThat(amountChangeCapture.value.getChangeAmount()).isEqualByComparingTo("20.00");
        assertThat(amountChangeCapture.value.getAuthorizedBefore()).isEqualByComparingTo("12.34");
        assertThat(amountChangeCapture.value.getAuthorizedAfter()).isEqualByComparingTo("32.34");
        assertThat(amountChangeCapture.value.getAvailableCaptureBefore()).isEqualByComparingTo("12.34");
        assertThat(amountChangeCapture.value.getAvailableCaptureAfter()).isEqualByComparingTo("32.34");
    }

    /**
     * Incremental Authorization 回调和主动查询并发确认成功时，只允许 CAS 成功的一方增加授权金额。
     */
    @Test
    void shouldIncreaseIncrementalAuthorizationAmountOnlyOnceWhenCallbackAndQueryRace() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionAmountChangeLogMapper amountChangeLogMapper = mock(TransactionAmountChangeLogMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        when(operationMapper.completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(1)
                .thenReturn(0);
        when(orderMapper.increaseAuthorizedAmount(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any()))
                .thenReturn(1);
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenReturn(1);
        when(amountChangeLogMapper.insertLogical(any(TransactionAmountChangeLogDO.class))).thenReturn(1);
        when(flowEventMapper.insertLogical(any(TransactionFlowEventDO.class))).thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                amountChangeLogMapper,
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        TransactionOperationDO operationDO = processingInitialOperation();
        operationDO.setTransactionId("TX202610011000000000003");
        operationDO.setSourceTransactionId("TX202607010030000000001");
        operationDO.setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        operationDO.setTransactionAmount(new BigDecimal("20.00"));
        operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        operationDO.setTransactionDateTime(LocalDateTime.of(2026, 10, 1, 10, 0));
        TransactionOrderDO orderDO = processingInitialOrder();
        orderDO.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        orderDO.setAuthorizedAmount(new BigDecimal("12.34"));
        orderDO.setAvailableCaptureAmount(new BigDecimal("12.34"));

        boolean callbackChanged = recordService.completeByChannelCallback(
                operationDO,
                orderDO,
                "CCB202610011000000000003",
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                null,
                null,
                "AUTHORIZED",
                "00",
                "Approved");
        boolean queryChanged = recordService.completeByChannelCallback(
                operationDO,
                orderDO,
                "CR202610011000000000003",
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                null,
                null,
                "AUTHORIZED",
                "00",
                "Approved by query");

        assertThat(callbackChanged).isTrue();
        assertThat(queryChanged).isFalse();
        verify(operationMapper, times(2)).completeStatus(
                any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyString());
        verify(orderMapper, times(1)).increaseAuthorizedAmount(
                "OP202607010030000000001",
                LocalDateTime.of(2026, 7, 1, 0, 30),
                "TX202610011000000000003",
                new BigDecimal("20.00"),
                0);
        verify(amountChangeLogMapper, times(1)).insertLogical(any(TransactionAmountChangeLogDO.class));
    }

    /**
     * 首次授权同步处理中时，渠道回调成功应按动作时间推进动作单，并初始化主单授权金额和可请款金额。
     */
    @Test
    void shouldCompleteInitialAuthorizationByChannelCallbackAndInitializeOrderAmounts() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        when(operationMapper.completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(1);
        when(orderMapper.markInitialSuccess(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any(), anyString()))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                notificationMapper,
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        boolean changed = recordService.completeByChannelCallback(
                processingInitialOperation(),
                processingInitialOrder(),
                "CCB202607010030000000001",
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                null,
                null,
                "AUTHORIZED",
                "00",
                "Approved");

        assertThat(changed).isTrue();
        verify(operationMapper).completeStatus(
                11L,
                LocalDateTime.of(2026, 7, 1, 0, 30),
                0,
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                PaymentProcessStageEnum.FINISHED.getCode(),
                null,
                null,
                "AUTHORIZED",
                "00",
                "Approved",
                null,
                null,
                null,
                "MATCHED");
        verify(orderMapper).markInitialSuccess(
                "OP202607010030000000001",
                LocalDateTime.of(2026, 7, 1, 0, 30),
                "TX202607010030000000001",
                new BigDecimal("12.34"),
                0,
                "MATCHED");
        verify(historyMapper, times(2)).insertLogical(any(TransactionStatusHistoryDO.class));
        verify(flowEventMapper).insertLogical(any());
        verify(notificationMapper).activateByTransactionId(
                anyString(), any(LocalDateTime.class), any(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    /**
     * S3-02：首次交易同步结果明确成功时，应在结果事务内先更新渠道请求，再通过动作单和主单 CAS 推进成功终态。
     */
    @Test
    void shouldCompleteInitialChannelResultWithCasWhenChannelApproved() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionChannelRequestMapper channelRequestMapper = mock(TransactionChannelRequestMapper.class);
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        TransactionMerchantApiInteractionLogMapper merchantApiLogMapper = mock(TransactionMerchantApiInteractionLogMapper.class);
        when(operationMapper.selectByTransactionId(
                "202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOperation());
        when(orderMapper.selectByOperationId(
                "OP202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestId(
                "CR202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(channelRequestFact("INIT", 0));
        when(channelRequestMapper.updateStatusLogical(anyString(), any(LocalDateTime.class), any(), any(), anyString(), any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(interactionLogMapper.updateByRequestIdLogical(anyString(), any(LocalDateTime.class), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(operationMapper.completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(1);
        when(orderMapper.markInitialSuccess(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any(), anyString()))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                interactionLogMapper,
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                notificationMapper,
                merchantApiLogMapper,
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setRequestId(commandDTO.getMerchantOrderId());
        commandDTO.setMerchantRequestPlainJsonMasked("{\"orderInfo\":{\"orderNo\":\"M202607140001\"}}");
        commandDTO.setOpenApiRequestTime(LocalDateTime.of(2026, 7, 1, 0, 29, 59));

        recordService.completeInitialChannelResult(
                commandDTO,
                routeResult(),
                initialResultInvokeResult("SUCCESS", channelResponse()),
                initialResultDTO(PaymentTransactionStatusEnum.SUCCESS.getCode(), PaymentProcessStageEnum.FINISHED.getCode()),
                PaymentRiskDecisionEnum.PASS,
                2);

        verify(channelRequestMapper).updateStatusLogical(
                "CR202607010030000000001",
                LocalDateTime.of(2026, 7, 1, 0, 30),
                0,
                List.of("INIT", "SENT", "TIMEOUT", "FAILED"),
                "SUCCESS",
                null,
                null,
                null,
                null,
                null,
                1,
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                null,
                LocalDateTime.of(2026, 7, 1, 0, 30, 2),
                1000);
        verify(interactionLogMapper).updateByRequestIdLogical(
                eq("CR202607010030000000001"),
                eq(LocalDateTime.of(2026, 7, 1, 0, 30)),
                eq("REQUEST_RESPONSE"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(1000),
                eq(LocalDateTime.of(2026, 7, 1, 0, 30, 2)));
        verify(operationMapper).completeStatus(
                11L,
                LocalDateTime.of(2026, 7, 1, 0, 30),
                0,
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                PaymentProcessStageEnum.FINISHED.getCode(),
                null,
                null,
                null,
                "00",
                "Approved",
                "123456",
                "RCPT001",
                "REF001",
                "NOT_REQUIRED");
        verify(orderMapper).markInitialSuccess(
                "OP202607010030000000001",
                LocalDateTime.of(2026, 7, 1, 0, 30),
                "202607010030000000001",
                new BigDecimal("12.34"),
                0,
                "NOT_REQUIRED");
        verify(historyMapper, times(2)).insertLogical(any(TransactionStatusHistoryDO.class));
        verify(flowEventMapper).insertLogical(any(TransactionFlowEventDO.class));
        verify(notificationMapper).activateByTransactionId(
                anyString(), any(LocalDateTime.class), any(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class));
        verify(merchantApiLogMapper).updateFinalResultLogical(
                eq("202607010030000000001"),
                eq(LocalDateTime.of(2026, 7, 1, 0, 30)),
                eq("AUTH202607140001"),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()),
                eq("T200"),
                eq("Success"),
                contains("\"transactionStatus\":\"SUCCESS\""),
                any(),
                any());
    }

    /**
     * 渠道结果回写必须复用受理阶段冻结的原始分片时间，不能从交易号重新解析后丢失微秒精度。
     */
    @Test
    void shouldLocateInitialOperationByOriginalTransactionDateTime() {
        LocalDateTime originalTransactionDateTime =
                LocalDateTime.of(2026, 7, 1, 0, 30, 0, 123_456_000);
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionOperationDO terminalOperation = processingInitialOperation();
        terminalOperation.setTransactionDateTime(originalTransactionDateTime);
        terminalOperation.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        TransactionOrderDO orderDO = processingInitialOrder();
        orderDO.setTransactionDateTime(originalTransactionDateTime);
        when(operationMapper.selectByTransactionId(
                "202607010030000000001", originalTransactionDateTime))
                .thenReturn(terminalOperation);
        when(orderMapper.selectByOperationId(
                "OP202607010030000000001", originalTransactionDateTime))
                .thenReturn(orderDO);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                mock(TransactionStatusHistoryMapper.class),
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setTransactionDateTime(originalTransactionDateTime);

        boolean changed = recordService.completeInitialChannelResultAndReport(
                commandDTO,
                routeResult(),
                initialResultInvokeResult("SUCCESS", channelResponse()),
                initialResultDTO(PaymentTransactionStatusEnum.SUCCESS.getCode(), PaymentProcessStageEnum.FINISHED.getCode()),
                PaymentRiskDecisionEnum.PASS,
                2);

        assertThat(changed).isFalse();
        verify(operationMapper).selectByTransactionId(
                "202607010030000000001", originalTransactionDateTime);
    }

    /**
     * 本地准备阶段的渠道交互事实缺失时必须回滚结果事务，禁止用结果阶段补插第二种事实模型。
     */
    @Test
    void shouldRejectChannelResultWhenPreparedInteractionFactIsMissing() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionChannelRequestMapper channelRequestMapper = mock(TransactionChannelRequestMapper.class);
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        when(operationMapper.selectByTransactionId(
                "202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOperation());
        when(orderMapper.selectByOperationId(
                "OP202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestId(
                "CR202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(channelRequestFact("INIT", 0));
        when(channelRequestMapper.updateStatusLogical(anyString(), any(LocalDateTime.class), any(), any(), anyString(),
                any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(interactionLogMapper.updateByRequestIdLogical(anyString(), any(LocalDateTime.class), anyString(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                interactionLogMapper,
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        assertThatThrownBy(() -> recordService.completeInitialChannelResult(
                baseCommand(),
                routeResult(),
                initialResultInvokeResult("SUCCESS", channelResponse()),
                initialResultDTO(PaymentTransactionStatusEnum.SUCCESS.getCode(), PaymentProcessStageEnum.FINISHED.getCode()),
                PaymentRiskDecisionEnum.PASS,
                2))
                .isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class)
                .hasMessageContaining("channel interaction fact can not be found");

        verify(interactionLogMapper, never()).insertLogical(any(TransactionChannelInteractionLogDO.class));
        verify(operationMapper, never()).completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any(), any(), anyString());
    }

    /**
     * 渠道结果 CAS 未命中但首个审计事实完全一致时，应按幂等重放继续推进交易状态。
     */
    @Test
    void shouldAcceptIdenticalChannelInteractionResultAfterCasMiss() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionChannelRequestMapper channelRequestMapper = mock(TransactionChannelRequestMapper.class);
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        Captured<TransactionChannelInteractionLogDO> persistedInteraction = new Captured<>();
        when(operationMapper.selectByTransactionId(
                "202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOperation());
        when(orderMapper.selectByOperationId(
                "OP202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestId(
                "CR202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(channelRequestFact("INIT", 0));
        when(channelRequestMapper.updateStatusLogical(anyString(), any(LocalDateTime.class), any(), any(), anyString(),
                any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(interactionLogMapper.updateByRequestIdLogical(anyString(), any(LocalDateTime.class), anyString(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    persistedInteraction.value = interactionResultFromUpdate(invocation.getArguments());
                    return 0;
                });
        when(interactionLogMapper.selectByRequestId(
                "CR202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenAnswer(invocation -> persistedInteraction.value);
        when(operationMapper.completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(),
                any(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(1);
        when(orderMapper.markInitialSuccess(anyString(), any(LocalDateTime.class), anyString(),
                any(BigDecimal.class), any(), anyString()))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                interactionLogMapper,
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        boolean changed = recordService.completeInitialChannelResultAndReport(
                baseCommand(),
                routeResult(),
                initialResultInvokeResult("SUCCESS", channelResponse()),
                initialResultDTO(PaymentTransactionStatusEnum.SUCCESS.getCode(), PaymentProcessStageEnum.FINISHED.getCode()),
                PaymentRiskDecisionEnum.PASS,
                2);

        assertThat(changed).isTrue();
        verify(operationMapper).completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(),
                any(), any(), any(), any(), any(), any(), any(), anyString());
        verify(orderMapper).markInitialSuccess(anyString(), any(LocalDateTime.class), anyString(),
                any(BigDecimal.class), any(), anyString());
    }

    /**
     * 渠道结果 CAS 未命中且首个审计事实不一致时，必须拒绝覆盖并停止交易状态推进。
     */
    @Test
    void shouldRejectChangedChannelInteractionResultAfterCasMiss() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionChannelRequestMapper channelRequestMapper = mock(TransactionChannelRequestMapper.class);
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        Captured<TransactionChannelInteractionLogDO> persistedInteraction = new Captured<>();
        when(operationMapper.selectByTransactionId(
                "202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOperation());
        when(orderMapper.selectByOperationId(
                "OP202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestId(
                "CR202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(channelRequestFact("INIT", 0));
        when(channelRequestMapper.updateStatusLogical(anyString(), any(LocalDateTime.class), any(), any(), anyString(),
                any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(interactionLogMapper.updateByRequestIdLogical(anyString(), any(LocalDateTime.class), anyString(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    persistedInteraction.value = interactionResultFromUpdate(invocation.getArguments());
                    persistedInteraction.value.setResponseBodyJsonMasked("{\"result\":\"DIFFERENT\"}");
                    return 0;
                });
        when(interactionLogMapper.selectByRequestId(
                "CR202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenAnswer(invocation -> persistedInteraction.value);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                mock(TransactionStatusHistoryMapper.class),
                channelRequestMapper,
                interactionLogMapper,
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        assertThatThrownBy(() -> recordService.completeInitialChannelResult(
                baseCommand(),
                routeResult(),
                initialResultInvokeResult("SUCCESS", channelResponse()),
                initialResultDTO(PaymentTransactionStatusEnum.SUCCESS.getCode(), PaymentProcessStageEnum.FINISHED.getCode()),
                PaymentRiskDecisionEnum.PASS,
                2))
                .isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class)
                .hasMessageContaining("channel interaction result has changed");

        verify(operationMapper, never()).completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any(), any(), anyString());
        verify(orderMapper, never()).markInitialSuccess(anyString(), any(LocalDateTime.class), anyString(),
                any(BigDecimal.class), any(), anyString());
    }

    /**
     * S3-04：首次交易同步结果仍为处理中时，应保留原 requestId 和渠道身份作为后续查询恢复入口。
     */
    @Test
    void shouldKeepInitialChannelResultRecoverableWhenChannelStillProcessing() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionChannelRequestMapper channelRequestMapper = mock(TransactionChannelRequestMapper.class);
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        when(operationMapper.selectByTransactionId(
                "202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOperation());
        when(orderMapper.selectByOperationId(
                "OP202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestId(
                "CR202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(channelRequestFact("INIT", 0));
        when(channelRequestMapper.updateStatusLogical(anyString(), any(LocalDateTime.class), any(), any(), anyString(), any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(interactionLogMapper.updateByRequestIdLogical(anyString(), any(LocalDateTime.class), anyString(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(operationMapper.updateNonTerminalChannelResult(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                interactionLogMapper,
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        PaymentCreateResultDTO resultDTO = initialResultDTO(
                PaymentTransactionStatusEnum.PROCESSING.getCode(),
                PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode());
        recordService.completeInitialChannelResult(
                baseCommand(),
                routeResult(),
                initialResultInvokeResult("SUCCESS", processingChannelResponse()),
                resultDTO,
                PaymentRiskDecisionEnum.PASS,
                2);

        ArgumentCaptor<LocalDateTime> matchTimeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(operationMapper).updateNonTerminalChannelResult(
                any(),
                any(LocalDateTime.class),
                any(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                matchTimeCaptor.capture());
        assertThat(matchTimeCaptor.getValue()).isNotNull();
        verify(operationMapper).updateNonTerminalChannelResult(
                11L,
                LocalDateTime.of(2026, 7, 1, 0, 30),
                0,
                PaymentTransactionStatusEnum.PROCESSING.getCode(),
                PaymentProcessStageEnum.CHANNEL_PROCESSING.getCode(),
                null,
                null,
                null,
                "PENDING",
                "PENDING",
                "Processing",
                "CR202607010030000000001",
                matchTimeCaptor.getValue());
        verify(orderMapper, never()).markInitialSuccess(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any(), anyString());
        verify(operationMapper, never()).completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyString());
        verify(historyMapper, times(2)).insertLogical(any(TransactionStatusHistoryDO.class));
        verify(flowEventMapper).insertLogical(any(TransactionFlowEventDO.class));
    }

    /**
     * S3-03：首次交易已经成功终态时，迟到失败同步结果不得覆盖动作单或主单终态。
     */
    @Test
    void shouldIgnoreInitialChannelResultWhenOperationAlreadyTerminalSuccess() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionChannelRequestMapper channelRequestMapper = mock(TransactionChannelRequestMapper.class);
        Captured<TransactionStatusHistoryDO> historyCapture = new Captured<>();
        TransactionOperationDO terminalOperation = processingInitialOperation();
        terminalOperation.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        when(operationMapper.selectByTransactionId(
                "202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(terminalOperation);
        when(orderMapper.selectByOperationId(
                "OP202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestId(
                "CR202607010030000000001", LocalDateTime.of(2026, 7, 1, 0, 30)))
                .thenReturn(channelRequestFact("SUCCESS", 1));
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenAnswer(invocation -> {
            historyCapture.value = invocation.getArgument(0);
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        recordService.completeInitialChannelResult(
                baseCommand(),
                routeResult(),
                initialResultInvokeResult("SUCCESS", failedChannelResponse()),
                initialResultDTO(PaymentTransactionStatusEnum.FAILED.getCode(), PaymentProcessStageEnum.FINISHED.getCode()),
                PaymentRiskDecisionEnum.PASS,
                2);

        assertThat(historyCapture.value.getTransitionResult()).isEqualTo("IGNORED");
        assertThat(historyCapture.value.getFailReason()).isEqualTo("operation is already terminal or state has changed");
        verify(operationMapper, never()).completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyString());
        verify(operationMapper, never()).updateNonTerminalChannelResult(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), anyString(), any());
        verify(orderMapper, never()).completeStatus(anyString(), any(LocalDateTime.class), anyString(), any(), anyString(), anyString(), any(), any(), any(), any(), anyString());
        verify(orderMapper, never()).markInitialSuccess(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any(), anyString());
    }

    /**
     * T-P0-09：交易动作已经进入成功终态后，迟到失败回调、超时结果或补偿结果不得覆盖成功终态。
     */
    @Test
    void shouldIgnoreCallbackWhenOperationAlreadyTerminalSuccess() {
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        Captured<TransactionStatusHistoryDO> historyCapture = new Captured<>();
        when(historyMapper.insertLogical(any(TransactionStatusHistoryDO.class))).thenAnswer(invocation -> {
            historyCapture.value = invocation.getArgument(0);
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                mock(TransactionChannelRequestMapper.class),
                mock(TransactionChannelInteractionLogMapper.class),
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        TransactionOperationDO operationDO = processingInitialOperation();
        operationDO.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());

        boolean changed = recordService.completeByChannelCallback(
                operationDO,
                processingInitialOrder(),
                "CCB202607010030000000002",
                PaymentTransactionStatusEnum.FAILED.getCode(),
                "CHANNEL_REQUEST_FAILED",
                "late failed callback",
                "FAILED",
                "05",
                "Declined");

        assertThat(changed).isFalse();
        assertThat(historyCapture.value.getTransitionResult()).isEqualTo("IGNORED");
        assertThat(historyCapture.value.getFailReason()).isEqualTo("operation is already terminal");
        verify(operationMapper, never()).completeStatus(any(), any(LocalDateTime.class), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), anyString());
        verify(orderMapper, never()).markInitialSuccess(anyString(), any(LocalDateTime.class), anyString(), any(BigDecimal.class), any(), anyString());
    }

    /**
     * 商户和渠道交互日志应按一笔交互一条记录保存，商户可见响应和通知载荷保持嵌套结构。
     */
    @Test
    void shouldRecordNestedMerchantPayloadAndCombinedChannelInteractionLog() {
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        TransactionMerchantApiInteractionLogMapper merchantApiLogMapper = mock(TransactionMerchantApiInteractionLogMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        Captured<TransactionChannelInteractionLogDO> interactionCapture = new Captured<>();
        Captured<TransactionMerchantApiInteractionLogDO> merchantApiCapture = new Captured<>();
        Captured<TransactionMerchantNotificationDO> notificationCapture = new Captured<>();
        when(interactionLogMapper.insertLogical(any(TransactionChannelInteractionLogDO.class))).thenAnswer(invocation -> {
            interactionCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(merchantApiLogMapper.insertLogical(any(TransactionMerchantApiInteractionLogDO.class))).thenAnswer(invocation -> {
            merchantApiCapture.value = invocation.getArgument(0);
            return 1;
        });
        when(notificationMapper.insertLogical(any(TransactionMerchantNotificationDO.class))).thenAnswer(invocation -> {
            notificationCapture.value = invocation.getArgument(0);
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                mock(TransactionOrderMapper.class),
                mock(TransactionOperationMapper.class),
                mock(TransactionStatusHistoryMapper.class),
                mock(TransactionChannelRequestMapper.class),
                interactionLogMapper,
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                notificationMapper,
                merchantApiLogMapper,
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setRequestId(commandDTO.getMerchantOrderId());
        commandDTO.setMerchantRequestCipherMasked("cipher***tail");
        commandDTO.setRequestFingerprint("request-digest");
        commandDTO.setMerchantRequestPlainJsonMasked("""
                {"orderInfo":{"orderNo":"M202607140001"},"cardInfo":{"cardNo":"512345******0008","securityCode":"***"}}
                """);
        commandDTO.setOpenApiRequestPath("/api/rest/payment/v1/authorization");
        commandDTO.setOpenApiRequestTime(LocalDateTime.of(2026, 7, 1, 0, 29, 59));
        commandDTO.setCallbackUrl("https://merchant.example/callback?source=qa");
        PaymentChannelInvokeResultDTO invokeResultDTO = channelInvokeResult();
        invokeResultDTO.setHttpMethod("POST");
        invokeResultDTO.setRequestUrlMasked("https://test-gateway.mastercard.com/api/rest/order/CODX260714180001/transaction/CH260714180001");
        invokeResultDTO.getChannelResponse().setHttpStatus(201);
        invokeResultDTO.getChannelResponse().setHttpMethod("PUT");
        invokeResultDTO.getChannelResponse().setRequestUrlMasked(
                "https://test-gateway.mastercard.com/api/rest/version/100/merchant/TESTDEVMER031/order/CODX260714180001/transaction/CH260714180001");
        invokeResultDTO.getChannelResponse().setRequestHeaderJsonMasked("{\"Authorization\":\"Basic ***\"}");
        invokeResultDTO.getChannelResponse().setRequestBodyJsonMasked(
                "{\"apiOperation\":\"AUTHORIZE\",\"sourceOfFunds\":{\"provided\":{\"card\":{\"number\":\"512345******0008\",\"securityCode\":\"***\"}}}}");
        invokeResultDTO.getChannelResponse().setResponseBodyJsonMasked(
                "{\"result\":\"SUCCESS\",\"order\":{\"id\":\"CODX260714180001\"},\"transaction\":{\"id\":\"CH260714180001\"},\"response\":{\"gatewayCode\":\"APPROVED\",\"acquirerCode\":\"00\"}}");
        invokeResultDTO.getChannelResponse().getRawResponse().put("httpStatus", "200");
        invokeResultDTO.getChannelResponse().getRawResponse().put("requestHeaderJsonMasked", "{\"Authorization\":\"Basic ***\"}");
        invokeResultDTO.getChannelResponse().getRawResponse().put("requestBodyJsonMasked",
                "{\"apiOperation\":\"AUTHORIZE\",\"sourceOfFunds\":{\"provided\":{\"card\":{\"number\":\"512345******0008\",\"securityCode\":\"***\"}}}}");
        invokeResultDTO.getChannelResponse().getRawResponse().put("responseBodyJsonMasked",
                "{\"result\":\"SUCCESS\",\"order\":{\"id\":\"CODX260714180001\"},\"transaction\":{\"id\":\"CH260714180001\"},\"response\":{\"gatewayCode\":\"APPROVED\",\"acquirerCode\":\"00\"}}");
        invokeResultDTO.setRequestStartTime(LocalDateTime.of(2026, 7, 1, 0, 30, 1));
        invokeResultDTO.setResponseTime(LocalDateTime.of(2026, 7, 1, 0, 30, 2));
        invokeResultDTO.setDurationMillis(1000);
        PaymentCreateResultDTO resultDTO = resultDTO();
        resultDTO.setMerchantId(commandDTO.getMerchantId());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        resultDTO.setMerchantResponseCode("T200");
        resultDTO.setMerchantResponseMessage("Success");
        resultDTO.setOrderAmount(commandDTO.getAmount());
        resultDTO.setOrderCurrency(commandDTO.getCurrency());
        resultDTO.setLabelAmount(commandDTO.getAmount());
        resultDTO.setLabelCurrency(commandDTO.getCurrency());
        resultDTO.setTransactionAmount(commandDTO.getAmount());
        resultDTO.setTransactionCurrency(commandDTO.getCurrency());
        resultDTO.setTransactionRate(new BigDecimal("1.00000000"));
        resultDTO.setTransactionDateTime(commandDTO.getTransactionDateTime());
        resultDTO.setTransactionTimeZone("Asia/Shanghai");
        resultDTO.setPaymentMethod(commandDTO.getPaymentMethod());
        resultDTO.setPaymentBrand("MASTERCARD");
        resultDTO.setCardBin("512345****0008");

        recordService.recordInitialTransaction(commandDTO, routeResult(), invokeResultDTO, resultDTO,
                PaymentRiskDecisionEnum.PASS, 2);

        assertThat(interactionCapture.value.getInteractionType()).isEqualTo("REQUEST_RESPONSE");
        assertThat(interactionCapture.value.getHttpMethod()).isEqualTo("PUT");
        assertThat(interactionCapture.value.getRequestUrlMasked()).contains("/version/100/merchant/TESTDEVMER031/order/CODX260714180001/transaction/CH260714180001");
        assertThat(interactionCapture.value.getRequestUrlMasked()).doesNotContain("/api/rest/order/");
        assertThat(interactionCapture.value.getHttpStatus()).isEqualTo(201);
        assertThat(interactionCapture.value.getRequestHeaderJsonMasked()).contains("Basic ***");
        assertThat(interactionCapture.value.getRequestBodyJsonMasked()).contains("\"apiOperation\":\"AUTHORIZE\"");
        assertThat(interactionCapture.value.getResponseBodyJsonMasked()).contains("\"result\":\"SUCCESS\"");
        assertThat(interactionCapture.value.getResponseBodyJsonMasked()).contains("\"transaction\":{\"id\":\"CH260714180001\"}");
        assertThat(interactionCapture.value.getDurationMillis()).isEqualTo(1000);
        assertNestedMerchantPayload(merchantApiCapture.value.getResponsePlainJsonMasked());
        assertThat(merchantApiCapture.value.getResponsePlainJsonMasked()).doesNotContain("\"merchantId\":\"200001\",");
        assertThat(merchantApiCapture.value.getResponsePlainJsonMasked()).doesNotContain("\"status\":\"SUCCESS\"");
        assertThat(merchantApiCapture.value.getResponsePlainJsonMasked()).doesNotContain("dccEnabled");
        assertThat(merchantApiCapture.value.getResponseCipherDigest()).isNull();
        assertThat(notificationCapture.value.getTargetUrlMasked()).isEqualTo("https://merchant.example/callback?***");
        assertThat(notificationCapture.value.getNotifyConfigSnapshotJson())
                .contains("https://merchant.example/callback?source=qa");
        assertThat(notificationCapture.value.getMaxRetryCount()).isEqualTo(10);
        assertNestedMerchantPayload(notificationCapture.value.getPayloadJsonMasked());
    }

    /** 渠道已生成并发出请求但网络异常时，应从请求扩展保存真实脱敏渠道报文。 */
    @Test
    void shouldRecordGeneratedChannelPayloadFromRequestWhenResponseIsMissing() {
        PaymentChannelInvokeResultDTO invokeResultDTO = channelInvokeResult();
        invokeResultDTO.setChannelResponse(null);
        invokeResultDTO.setExceptionType("ChannelRequestException");
        invokeResultDTO.setExceptionMessage("MPGS network request failed");
        invokeResultDTO.getChannelRequest().getExtension().put("httpMethod", "PUT");
        invokeResultDTO.getChannelRequest().getExtension().put("requestUrlMasked",
                "https://test-gateway.mastercard.com/api/rest/version/100/merchant/TESTDEVMER031/order/TX260714180001/transaction/CH260714180001");
        invokeResultDTO.getChannelRequest().getExtension().put("requestHeaderJsonMasked",
                "{\"Authorization\":\"Basic ***\"}");
        invokeResultDTO.getChannelRequest().getExtension().put("requestBodyJsonMasked",
                "{\"apiOperation\":\"AUTHORIZE\",\"sourceOfFunds\":{\"provided\":{\"card\":{\"number\":\"512345******0008\",\"securityCode\":\"***\"}}}}");

        TransactionChannelInteractionLogDO interactionDO = recordInitialInteraction(invokeResultDTO);

        assertThat(interactionDO.getHttpMethod()).isEqualTo("PUT");
        assertThat(interactionDO.getRequestUrlMasked()).contains("/version/100/merchant/TESTDEVMER031/");
        assertThat(interactionDO.getRequestHeaderJsonMasked()).isEqualTo("{\"Authorization\":\"Basic ***\"}");
        assertThat(interactionDO.getRequestBodyJsonMasked()).contains("\"apiOperation\":\"AUTHORIZE\"");
        assertThat(interactionDO.getRequestBodyJsonMasked()).doesNotContain("\"channelCode\"");
    }

    /** 渠道发送前校验失败时没有真实 HTTP 请求，后台不得把内部统一请求对象冒充渠道报文。 */
    @Test
    void shouldLeaveChannelPayloadEmptyWhenRequestWasNotGenerated() {
        PaymentChannelInvokeResultDTO invokeResultDTO = channelInvokeResult();
        invokeResultDTO.setChannelResponse(null);
        invokeResultDTO.setExceptionType("ChannelRequestException");
        invokeResultDTO.setExceptionMessage("MPGS merchantId is required");

        TransactionChannelInteractionLogDO interactionDO = recordInitialInteraction(invokeResultDTO);

        assertThat(interactionDO.getRequestHeaderJsonMasked()).isNull();
        assertThat(interactionDO.getRequestBodyJsonMasked()).isNull();
    }

    /** 执行首次交易事实写入并捕获渠道交互日志。 */
    private TransactionChannelInteractionLogDO recordInitialInteraction(PaymentChannelInvokeResultDTO invokeResultDTO) {
        TransactionChannelInteractionLogMapper interactionLogMapper = mock(TransactionChannelInteractionLogMapper.class);
        Captured<TransactionChannelInteractionLogDO> interactionCapture = new Captured<>();
        when(interactionLogMapper.insertLogical(any(TransactionChannelInteractionLogDO.class))).thenAnswer(invocation -> {
            interactionCapture.value = invocation.getArgument(0);
            return 1;
        });
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                mock(TransactionOrderMapper.class),
                mock(TransactionOperationMapper.class),
                mock(TransactionStatusHistoryMapper.class),
                mock(TransactionChannelRequestMapper.class),
                interactionLogMapper,
                mock(TransactionFlowEventMapper.class),
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                new TransactionShardingKeyParser(),
                logicalShardingProperties());

        recordService.recordInitialTransaction(baseCommand(), routeResult(), invokeResultDTO, resultDTO(),
                PaymentRiskDecisionEnum.PASS, 2);
        return interactionCapture.value;
    }

    private PaymentCreateCommandDTO baseCommand() {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202607140001");
        commandDTO.setMerchantOrderId("AUTH202607140001");
        commandDTO.setPaymentMethod("BANK_CARD");
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 1, 0, 30));
        commandDTO.setRequestSource("HOSTED_CHECKOUT");
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfoDTO.setCardBrand("MASTERCARD");
        transactionInfoDTO.setIssuerCountry("AE");
        transactionInfoDTO.setMerchantWebsite("https://merchant.example.com/checkout");
        commandDTO.setTransactionInfo(transactionInfoDTO);
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCreateCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("5123456789010008");
        cardInfoDTO.setExpirationMonth("01");
        cardInfoDTO.setExpirationYear("39");
        commandDTO.setCardInfo(cardInfoDTO);
        return commandDTO;
    }

    private TransactionFollowUpRecordDTO followUpRecord() {
        TransactionOrderDO sourceOrderDO = new TransactionOrderDO();
        sourceOrderDO.setOperationId("OP202607010030000000001");
        sourceOrderDO.setRootTransactionId("TX202607010030000000001");
        sourceOrderDO.setMerchantId("200001");
        sourceOrderDO.setMerchantOrderNo("M202607140001");
        sourceOrderDO.setMerchantOrderId("AUTH202607140001");
        sourceOrderDO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        sourceOrderDO.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        sourceOrderDO.setTransactionCurrency("USD");
        sourceOrderDO.setTransactionAmount(new BigDecimal("12.34"));
        sourceOrderDO.setAuthorizedAmount(new BigDecimal("12.34"));
        sourceOrderDO.setAuthorizedCancelAmount(BigDecimal.ZERO);
        sourceOrderDO.setCapturedAmount(BigDecimal.ZERO);
        sourceOrderDO.setRefundedAmount(BigDecimal.ZERO);
        sourceOrderDO.setAvailableCaptureAmount(new BigDecimal("12.34"));
        sourceOrderDO.setAvailableRefundAmount(BigDecimal.ZERO);
        sourceOrderDO.setCurrencyExponent(2);
        sourceOrderDO.setTransactionDateTime(LocalDateTime.of(2026, 7, 1, 0, 30));
        sourceOrderDO.setTransactionUtcTime(LocalDateTime.of(2026, 6, 30, 16, 30));
        sourceOrderDO.setTransactionTimeZone("Asia/Shanghai");
        sourceOrderDO.setVersion(0);

        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setMerchantOrderId("CAPTURE202610010001");
        commandDTO.setTransactionType(PaymentTransactionTypeEnum.CAPTURE.getCode());
        commandDTO.setAmount(new BigDecimal("5.00"));
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 10, 1, 10, 0));
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfoDTO.setSourceTransactionId(sourceOrderDO.getRootTransactionId());
        commandDTO.setTransactionInfo(transactionInfoDTO);

        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId(sourceOrderDO.getOperationId());
        resultDTO.setTransactionId("TX202610011000000000001");
        resultDTO.setSourceTransactionId(sourceOrderDO.getRootTransactionId());
        resultDTO.setMerchantOrderNo(sourceOrderDO.getMerchantOrderNo());
        resultDTO.setMerchantOrderId(commandDTO.getMerchantOrderId());
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.CAPTURE.getCode());
        resultDTO.setStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        resultDTO.setAmount(500L);
        resultDTO.setCurrency("USD");

        TransactionFollowUpRecordDTO recordDTO = new TransactionFollowUpRecordDTO();
        recordDTO.setSourceOrderDO(sourceOrderDO);
        recordDTO.setCommandDTO(commandDTO);
        recordDTO.setRouteResultDTO(routeResult());
        recordDTO.setChannelInvokeResultDTO(channelInvokeResult());
        recordDTO.setChannelResponse(channelResponse());
        recordDTO.setResultDTO(resultDTO);
        recordDTO.setCurrencyExponent(2);
        return recordDTO;
    }

    private TransactionOperationDO processingInitialOperation() {
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setId(11L);
        operationDO.setOperationId("OP202607010030000000001");
        operationDO.setTransactionId("TX202607010030000000001");
        operationDO.setMerchantId("200001");
        operationDO.setMerchantOrderNo("M202607140001");
        operationDO.setMerchantOrderId("AUTH202607140001");
        operationDO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        operationDO.setTransactionCurrency("USD");
        operationDO.setTransactionAmount(new BigDecimal("12.34"));
        operationDO.setTransactionDateTime(LocalDateTime.of(2026, 7, 1, 0, 30));
        operationDO.setVersion(0);
        return operationDO;
    }

    private TransactionOrderDO processingInitialOrder() {
        TransactionOrderDO orderDO = new TransactionOrderDO();
        orderDO.setOperationId("OP202607010030000000001");
        orderDO.setRootTransactionId("TX202607010030000000001");
        orderDO.setLatestTransactionId("TX202607010030000000001");
        orderDO.setMerchantId("200001");
        orderDO.setMerchantOrderNo("M202607140001");
        orderDO.setMerchantOrderId("AUTH202607140001");
        orderDO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        orderDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        orderDO.setTransactionCurrency("USD");
        orderDO.setTransactionAmount(new BigDecimal("12.34"));
        orderDO.setAuthorizedAmount(BigDecimal.ZERO);
        orderDO.setAuthorizedCancelAmount(BigDecimal.ZERO);
        orderDO.setCapturedAmount(BigDecimal.ZERO);
        orderDO.setRefundedAmount(BigDecimal.ZERO);
        orderDO.setChargebackAmount(BigDecimal.ZERO);
        orderDO.setCurrencyExponent(2);
        orderDO.setTransactionDateTime(LocalDateTime.of(2026, 7, 1, 0, 30));
        orderDO.setVersion(0);
        return orderDO;
    }

    private PaymentCreateResultDTO resultDTO() {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId("OP260714180001");
        resultDTO.setTransactionId("TX260714180001");
        resultDTO.setMerchantOrderNo("M202607140001");
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        resultDTO.setStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        resultDTO.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        resultDTO.setAmount(1234L);
        resultDTO.setCurrency("USD");
        return resultDTO;
    }

    private com.scott.payment.payment.service.dto.PaymentRouteResultDTO routeResult() {
        com.scott.payment.payment.service.dto.PaymentRouteResultDTO routeResultDTO =
                com.scott.payment.payment.service.dto.PaymentRouteResultDTO.routed("MPGS");
        routeResultDTO.setChannelId(101L);
        routeResultDTO.setMidConfigId(1001L);
        routeResultDTO.setMidNo("TESTDEVMER031");
        return routeResultDTO;
    }

    private ChannelPaymentResponse channelResponse() {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("MPGS");
        response.setChannelOrderNo("CODX260714180001");
        response.setChannelResponseCode("00");
        response.setChannelResponseMessage("Approved");
        response.setAuthCode("123456");
        response.setRrn("RCPT001");
        response.setAcquirerReferenceNo("REF001");
        return response;
    }

    private PaymentChannelInvokeResultDTO channelInvokeResult() {
        PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
        resultDTO.setRequestId("CR260714180001");
        resultDTO.setRequestStatus("SUCCESS");
        resultDTO.setChannelRequest(channelRequest());
        resultDTO.setChannelResponse(channelResponse());
        return resultDTO;
    }

    private PaymentCreateResultDTO initialResultDTO(String status, String processStage) {
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setOperationId("OP202607010030000000001");
        resultDTO.setTransactionId("202607010030000000001");
        resultDTO.setMerchantOrderNo("M202607140001");
        resultDTO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        resultDTO.setStatus(status);
        resultDTO.setProcessStage(processStage);
        resultDTO.setAmount(1234L);
        resultDTO.setCurrency("USD");
        return resultDTO;
    }

    private PaymentChannelInvokeResultDTO initialResultInvokeResult(String requestStatus, ChannelPaymentResponse response) {
        PaymentChannelInvokeResultDTO resultDTO = new PaymentChannelInvokeResultDTO();
        resultDTO.setRequestId("CR202607010030000000001");
        resultDTO.setRequestStatus(requestStatus);
        ChannelPaymentRequest request = channelRequest();
        request.setChannelOrderNo("202607010030000000001");
        request.setChannelTransactionId("CH202607010030000000001");
        resultDTO.setChannelRequest(request);
        resultDTO.setChannelResponse(response);
        resultDTO.setResponseTime(LocalDateTime.of(2026, 7, 1, 0, 30, 2));
        resultDTO.setDurationMillis(1000);
        return resultDTO;
    }

    private TransactionChannelRequestDO channelRequestFact(String requestStatus, int version) {
        TransactionChannelRequestDO requestDO = new TransactionChannelRequestDO();
        requestDO.setRequestId("CR202607010030000000001");
        requestDO.setRequestStatus(requestStatus);
        requestDO.setPlatformResultCode(PaymentTransactionStatusEnum.SUCCESS.getCode().equals(requestStatus)
                ? PaymentTransactionStatusEnum.SUCCESS.getCode()
                : PaymentTransactionStatusEnum.PROCESSING.getCode());
        requestDO.setVersion(version);
        return requestDO;
    }

    private TransactionChannelInteractionLogDO interactionResultFromUpdate(Object[] arguments) {
        TransactionChannelInteractionLogDO interactionDO = new TransactionChannelInteractionLogDO();
        interactionDO.setInteractionType((String) arguments[2]);
        interactionDO.setHttpMethod((String) arguments[3]);
        interactionDO.setRequestUrlMasked((String) arguments[4]);
        interactionDO.setHttpStatus((Integer) arguments[5]);
        interactionDO.setRequestHeaderJsonMasked((String) arguments[6]);
        interactionDO.setRequestBodyJsonMasked((String) arguments[7]);
        interactionDO.setResponseHeaderJsonMasked((String) arguments[8]);
        interactionDO.setResponseBodyJsonMasked((String) arguments[9]);
        interactionDO.setExceptionType((String) arguments[10]);
        interactionDO.setExceptionMessage((String) arguments[11]);
        interactionDO.setDurationMillis((Integer) arguments[12]);
        return interactionDO;
    }

    private ChannelPaymentResponse processingChannelResponse() {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("MPGS");
        response.setChannelOrderNo("202607010030000000001");
        response.setChannelTransactionId("CH202607010030000000001");
        response.setChannelTradeStatus("PROCESSING");
        response.setRawChannelStatus("PENDING");
        response.setChannelResponseCode("PENDING");
        response.setChannelResponseMessage("Processing");
        return response;
    }

    private ChannelPaymentResponse failedChannelResponse() {
        ChannelPaymentResponse response = new ChannelPaymentResponse();
        response.setChannelCode("MPGS");
        response.setChannelOrderNo("202607010030000000001");
        response.setChannelTransactionId("CH202607010030000000001");
        response.setChannelTradeStatus("FAILED");
        response.setRawChannelStatus("FAILED");
        response.setChannelResponseCode("05");
        response.setChannelResponseMessage("Declined");
        return response;
    }

    private ChannelPaymentRequest channelRequest() {
        ChannelPaymentRequest request = new ChannelPaymentRequest();
        request.setChannelCode("MPGS");
        request.setChannelOrderNo("TX260714180001");
        request.setChannelTransactionId("CH260714180001");
        request.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        request.setAmount(new BigDecimal("12.34"));
        request.setCurrency("USD");
        return request;
    }

    private TransactionShardingProperties logicalShardingProperties() {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        return properties;
    }

    private static class Captured<T> {

        /**
         * value，用于保存 Captured 中与 value 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private T value;
    }

    private static class CapturedList<T> {

        /**
         * values，用于保存 Captured List 中与 values 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final List<T> values = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private void assertNestedMerchantPayload(String payloadJson) {
        Map<String, Object> payload = com.scott.payment.component.core.json.JsonUtils.parseObject(payloadJson, new com.alibaba.fastjson2.TypeReference<>() {
        });
        assertThat(payload).containsKeys("merchantInfo", "orderInfo", "transactionInfo", "billingInfo");
        assertThat(payload).doesNotContainKeys("merchantId", "orderNo", "orderId", "transactionId", "status", "currency", "amount");
        assertThat((Map<String, Object>) payload.get("merchantInfo")).containsEntry("merchantId", "200001");
        assertThat((Map<String, Object>) payload.get("orderInfo")).containsEntry("orderNo", "M202607140001");
        assertThat((Map<String, Object>) payload.get("transactionInfo")).containsEntry("transactionId", "TX260714180001");
        assertThat((Map<String, Object>) payload.get("billingInfo")).containsEntry("transactionCurrency", "USD");
    }
}
