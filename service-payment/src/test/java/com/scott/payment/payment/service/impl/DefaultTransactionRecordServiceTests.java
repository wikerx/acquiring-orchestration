package com.scott.payment.payment.service.impl;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.ShardingTableRangeResolver;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 * @description : 交易事实记录服务单元测试，验证首次交易主单、动作单和状态历史按 transaction_date_time 路由分表并写入关键字段。
 * @status : create
 */
class DefaultTransactionRecordServiceTests {

    /**
     * 首次交易事实应写入交易时间所在季度物理表，并保留业务时区与 UTC 时间字段。
     */
    @Test
    void shouldRecordInitialTransactionToQuarterPhysicalTables() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        TransactionPaymentMethodInfoMapper paymentMethodInfoMapper = mock(TransactionPaymentMethodInfoMapper.class);
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        Captured<TransactionOrderDO> orderCapture = new Captured<>();
        Captured<TransactionOperationDO> operationCapture = new Captured<>();
        Captured<TransactionStatusHistoryDO> historyCapture = new Captured<>();
        Captured<TransactionPaymentMethodInfoDO> paymentInfoCapture = new Captured<>();
        when(orderMapper.insertPhysical(anyString(), any(TransactionOrderDO.class))).thenAnswer(invocation -> {
            orderCapture.physicalTable = invocation.getArgument(0);
            orderCapture.value = invocation.getArgument(1);
            return 1;
        });
        when(operationMapper.insertPhysical(anyString(), any(TransactionOperationDO.class))).thenAnswer(invocation -> {
            operationCapture.physicalTable = invocation.getArgument(0);
            operationCapture.value = invocation.getArgument(1);
            return 1;
        });
        when(historyMapper.insertPhysical(anyString(), any(TransactionStatusHistoryDO.class))).thenAnswer(invocation -> {
            historyCapture.physicalTable = invocation.getArgument(0);
            historyCapture.value = invocation.getArgument(1);
            return 1;
        });
        when(paymentMethodInfoMapper.insertPhysical(anyString(), any(TransactionPaymentMethodInfoDO.class))).thenAnswer(invocation -> {
            paymentInfoCapture.physicalTable = invocation.getArgument(0);
            paymentInfoCapture.value = invocation.getArgument(1);
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());

        recordService.recordInitialTransaction(baseCommand(), routeResult(), channelInvokeResult(), resultDTO(),
                PaymentRiskDecisionEnum.PASS, 2);

        assertThat(orderCapture.physicalTable).isEqualTo("transaction_order_202603");
        assertThat(operationCapture.physicalTable).isEqualTo("transaction_operation_202603");
        assertThat(historyCapture.physicalTable).isEqualTo("transaction_status_history_202603");
        assertThat(orderCapture.value.getOperationId()).isEqualTo("OP260714180001");
        assertThat(orderCapture.value.getRootTransactionId()).isEqualTo("TX260714180001");
        assertThat(orderCapture.value.getTransactionUtcTime()).isEqualTo(LocalDateTime.of(2026, 6, 30, 16, 30));
        assertThat(orderCapture.value.getAuthorizedAmount()).isEqualByComparingTo("12.34");
        assertThat(orderCapture.value.getAvailableCaptureAmount()).isEqualByComparingTo("12.34");
        assertThat(operationCapture.value.getChannelResponseCode()).isEqualTo("00");
        assertThat(operationCapture.value.getAuthCode()).isEqualTo("123456");
        assertThat(operationCapture.value.getRrn()).isEqualTo("RCPT001");
        assertThat(operationCapture.value.getAcquirerReferenceNo()).isEqualTo("REF001");
        assertThat(operationCapture.value.getApprovedAmount()).isEqualByComparingTo("12.34");
        assertThat(operationCapture.value.getMerchantOperationNo()).isEqualTo("M202607140001");
        assertThat(paymentInfoCapture.physicalTable).isEqualTo("transaction_payment_method_info_202603");
        assertThat(paymentInfoCapture.value.getPaymentMethod()).isEqualTo("BANK_CARD");
        assertThat(paymentInfoCapture.value.getPaymentBrand()).isEqualTo("MASTERCARD");
        assertThat(paymentInfoCapture.value.getCardBin()).isEqualTo("51234567");
        assertThat(paymentInfoCapture.value.getCardLast4()).isEqualTo("0008");
        assertThat(paymentInfoCapture.value.getCardNumberMasked()).doesNotContain("5123456789010008");
        assertThat(paymentInfoCapture.value.getPaymentAccountHash()).isNotBlank();
        verify(historyMapper, times(2)).insertPhysical(anyString(), any(TransactionStatusHistoryDO.class));
    }

    /**
     * 渠道 HTTP 调用完成但渠道业务返回失败时，流程事件必须按业务失败落库，避免后台时间轴显示绿色成功。
     */
    @Test
    void shouldMarkChannelFlowEventFailedWhenChannelBusinessResultFailed() {
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        CapturedList<TransactionFlowEventDO> flowEventCapture = new CapturedList<>();
        when(flowEventMapper.insertPhysical(anyString(), any(TransactionFlowEventDO.class))).thenAnswer(invocation -> {
            flowEventCapture.values.add(invocation.getArgument(1));
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());
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
        assertThat(statusEvent.getErrorCode()).isEqualTo("CHANNEL_REQUEST_FAILED");
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
        when(operationMapper.countByOperationIdPhysical(anyString(), anyString())).thenReturn(1);
        when(operationMapper.insertPhysical(anyString(), any(TransactionOperationDO.class))).thenAnswer(invocation -> {
            operationCapture.physicalTable = invocation.getArgument(0);
            operationCapture.value = invocation.getArgument(1);
            return 1;
        });
        when(historyMapper.insertPhysical(anyString(), any(TransactionStatusHistoryDO.class))).thenAnswer(invocation -> {
            historyCapture.physicalTable = invocation.getArgument(0);
            historyCapture.value = invocation.getArgument(1);
            return 1;
        });
        when(orderMapper.increaseCapturedAmountPhysical(anyString(), anyString(), anyString(), any(BigDecimal.class), any()))
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());

        recordService.recordFollowUpTransaction(followUpRecord());

        assertThat(operationCapture.physicalTable).isEqualTo("transaction_operation_202604");
        assertThat(operationCapture.value.getTransactionId()).isEqualTo("TX202610011000000000001");
        assertThat(operationCapture.value.getTransactionDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        assertThat(operationCapture.value.getSourceOperationId()).isEqualTo("OP202607010030000000001");
        assertThat(operationCapture.value.getMerchantOperationNo()).isEqualTo("CAPTURE202610010001");
        assertThat(historyCapture.physicalTable).isEqualTo("transaction_status_history_202604");
        verify(orderMapper).increaseCapturedAmountPhysical(
                "transaction_order_202603",
                "OP202607010030000000001",
                "TX202610011000000000001",
                new BigDecimal("5.00"),
                0);
    }

    /**
     * 授权撤销成功后，主单可请款金额必须被清零，避免页面和后续请款校验继续显示可用额度。
     */
    @Test
    void shouldClearAvailableCaptureAmountWhenVoidSucceeds() {
        TransactionOrderMapper orderMapper = mock(TransactionOrderMapper.class);
        TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        TransactionStatusHistoryMapper historyMapper = mock(TransactionStatusHistoryMapper.class);
        when(operationMapper.countByOperationIdPhysical(anyString(), anyString())).thenReturn(1);
        when(operationMapper.insertPhysical(anyString(), any(TransactionOperationDO.class))).thenReturn(1);
        when(orderMapper.markVoidSuccessPhysical(anyString(), anyString(), anyString(), any())).thenReturn(1);
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());

        TransactionFollowUpRecordDTO recordDTO = followUpRecord();
        recordDTO.getCommandDTO().setTransactionType(PaymentTransactionTypeEnum.VOID.getCode());
        recordDTO.getCommandDTO().setAmount(null);
        recordDTO.getResultDTO().setTransactionType(PaymentTransactionTypeEnum.VOID.getCode());
        recordDTO.getResultDTO().setTransactionId("TX202610011000000000002");
        recordDTO.getResultDTO().setAmount(1234L);

        recordService.recordFollowUpTransaction(recordDTO);

        verify(orderMapper).markVoidSuccessPhysical(
                "transaction_order_202603",
                "OP202607010030000000001",
                "TX202610011000000000002",
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
        when(operationMapper.countByOperationIdPhysical(anyString(), anyString())).thenReturn(1);
        when(operationMapper.insertPhysical(anyString(), any(TransactionOperationDO.class))).thenReturn(1);
        when(orderMapper.increaseAuthorizedAmountPhysical(anyString(), anyString(), anyString(), any(BigDecimal.class), any()))
                .thenReturn(1);
        when(amountChangeLogMapper.insertPhysical(anyString(), any(TransactionAmountChangeLogDO.class))).thenAnswer(invocation -> {
            amountChangeCapture.physicalTable = invocation.getArgument(0);
            amountChangeCapture.value = invocation.getArgument(1);
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());
        TransactionFollowUpRecordDTO recordDTO = followUpRecord();
        recordDTO.getCommandDTO().setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        recordDTO.getCommandDTO().setAmount(new BigDecimal("20.00"));
        recordDTO.getResultDTO().setTransactionType(PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION.getCode());
        recordDTO.getResultDTO().setTransactionId("TX202610011000000000003");
        recordDTO.getResultDTO().setAmount(2000L);

        recordService.recordFollowUpTransaction(recordDTO);

        verify(orderMapper).increaseAuthorizedAmountPhysical(
                "transaction_order_202603",
                "OP202607010030000000001",
                "TX202610011000000000003",
                new BigDecimal("20.00"),
                0);
        assertThat(amountChangeCapture.physicalTable).isEqualTo("transaction_amount_change_log_202604");
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
        when(operationMapper.completeStatusPhysical(anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(1)
                .thenReturn(0);
        when(orderMapper.increaseAuthorizedAmountPhysical(anyString(), anyString(), anyString(), any(BigDecimal.class), any()))
                .thenReturn(1);
        when(historyMapper.insertPhysical(anyString(), any(TransactionStatusHistoryDO.class))).thenReturn(1);
        when(amountChangeLogMapper.insertPhysical(anyString(), any(TransactionAmountChangeLogDO.class))).thenReturn(1);
        when(flowEventMapper.insertPhysical(anyString(), any(TransactionFlowEventDO.class))).thenReturn(1);
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());
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
        verify(operationMapper, times(2)).completeStatusPhysical(
                anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any());
        verify(orderMapper, times(1)).increaseAuthorizedAmountPhysical(
                "transaction_order_202603",
                "OP202607010030000000001",
                "TX202610011000000000003",
                new BigDecimal("20.00"),
                0);
        verify(amountChangeLogMapper, times(1)).insertPhysical(anyString(), any(TransactionAmountChangeLogDO.class));
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
        when(operationMapper.completeStatusPhysical(anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(orderMapper.markInitialSuccessPhysical(anyString(), anyString(), anyString(), any(BigDecimal.class), any()))
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());

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
        verify(operationMapper).completeStatusPhysical(
                "transaction_operation_202603",
                11L,
                0,
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                PaymentProcessStageEnum.FINISHED.getCode(),
                null,
                null,
                "AUTHORIZED",
                "00",
                "Approved");
        verify(orderMapper).markInitialSuccessPhysical(
                "transaction_order_202603",
                "OP202607010030000000001",
                "TX202607010030000000001",
                new BigDecimal("12.34"),
                0);
        verify(historyMapper, times(2)).insertPhysical(anyString(), any(TransactionStatusHistoryDO.class));
        verify(flowEventMapper).insertPhysical(anyString(), any());
        verify(notificationMapper).activateByTransactionId(anyString(), anyString(), anyString(), any(), any());
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
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        when(operationMapper.selectByTransactionIdPhysical("transaction_operation_202603", "202607010030000000001"))
                .thenReturn(processingInitialOperation());
        when(orderMapper.selectByOperationIdPhysical("transaction_order_202603", "OP202607010030000000001"))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestIdPhysical("transaction_channel_request_202603", "CR202607010030000000001"))
                .thenReturn(channelRequestFact("INIT", 0));
        when(channelRequestMapper.updateStatusPhysical(anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(operationMapper.completeStatusPhysical(anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(orderMapper.markInitialSuccessPhysical(anyString(), anyString(), anyString(), any(BigDecimal.class), any()))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                notificationMapper,
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                shardingDataTemplate(),
                new TransactionShardingKeyParser());

        recordService.completeInitialChannelResult(
                baseCommand(),
                routeResult(),
                initialResultInvokeResult("SUCCESS", channelResponse()),
                initialResultDTO(PaymentTransactionStatusEnum.SUCCESS.getCode(), PaymentProcessStageEnum.FINISHED.getCode()),
                PaymentRiskDecisionEnum.PASS,
                2);

        verify(channelRequestMapper).updateStatusPhysical(
                "transaction_channel_request_202603",
                "CR202607010030000000001",
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
        verify(operationMapper).completeStatusPhysical(
                "transaction_operation_202603",
                11L,
                0,
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                PaymentProcessStageEnum.FINISHED.getCode(),
                null,
                null,
                null,
                "00",
                "Approved");
        verify(orderMapper).markInitialSuccessPhysical(
                "transaction_order_202603",
                "OP202607010030000000001",
                "202607010030000000001",
                new BigDecimal("12.34"),
                0);
        verify(historyMapper, times(2)).insertPhysical(anyString(), any(TransactionStatusHistoryDO.class));
        verify(flowEventMapper).insertPhysical(anyString(), any(TransactionFlowEventDO.class));
        verify(notificationMapper).activateByTransactionId(anyString(), anyString(), anyString(), any(), any());
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
        TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        when(operationMapper.selectByTransactionIdPhysical("transaction_operation_202603", "202607010030000000001"))
                .thenReturn(processingInitialOperation());
        when(orderMapper.selectByOperationIdPhysical("transaction_order_202603", "OP202607010030000000001"))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestIdPhysical("transaction_channel_request_202603", "CR202607010030000000001"))
                .thenReturn(channelRequestFact("INIT", 0));
        when(channelRequestMapper.updateStatusPhysical(anyString(), anyString(), any(), any(), anyString(), any(), any(), any(), any(), any(), any(), anyString(), any(), any(), any()))
                .thenReturn(1);
        when(operationMapper.updateNonTerminalChannelResultPhysical(anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(1);
        DefaultTransactionRecordService recordService = new DefaultTransactionRecordService(
                orderMapper,
                operationMapper,
                historyMapper,
                channelRequestMapper,
                mock(TransactionChannelInteractionLogMapper.class),
                flowEventMapper,
                mock(TransactionAmountChangeLogMapper.class),
                mock(TransactionMerchantNotificationMapper.class),
                mock(TransactionMerchantApiInteractionLogMapper.class),
                mock(TransactionPaymentMethodInfoMapper.class),
                shardingDataTemplate(),
                new TransactionShardingKeyParser());

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
        verify(operationMapper).updateNonTerminalChannelResultPhysical(
                anyString(),
                any(),
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
        verify(operationMapper).updateNonTerminalChannelResultPhysical(
                "transaction_operation_202603",
                11L,
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
        verify(orderMapper, never()).markInitialSuccessPhysical(anyString(), anyString(), anyString(), any(BigDecimal.class), any());
        verify(operationMapper, never()).completeStatusPhysical(anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any());
        verify(historyMapper, times(2)).insertPhysical(anyString(), any(TransactionStatusHistoryDO.class));
        verify(flowEventMapper).insertPhysical(anyString(), any(TransactionFlowEventDO.class));
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
        when(operationMapper.selectByTransactionIdPhysical("transaction_operation_202603", "202607010030000000001"))
                .thenReturn(terminalOperation);
        when(orderMapper.selectByOperationIdPhysical("transaction_order_202603", "OP202607010030000000001"))
                .thenReturn(processingInitialOrder());
        when(channelRequestMapper.selectByRequestIdPhysical("transaction_channel_request_202603", "CR202607010030000000001"))
                .thenReturn(channelRequestFact("SUCCESS", 1));
        when(historyMapper.insertPhysical(anyString(), any(TransactionStatusHistoryDO.class))).thenAnswer(invocation -> {
            historyCapture.physicalTable = invocation.getArgument(0);
            historyCapture.value = invocation.getArgument(1);
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());

        recordService.completeInitialChannelResult(
                baseCommand(),
                routeResult(),
                initialResultInvokeResult("SUCCESS", failedChannelResponse()),
                initialResultDTO(PaymentTransactionStatusEnum.FAILED.getCode(), PaymentProcessStageEnum.FINISHED.getCode()),
                PaymentRiskDecisionEnum.PASS,
                2);

        assertThat(historyCapture.physicalTable).isEqualTo("transaction_status_history_202603");
        assertThat(historyCapture.value.getTransitionResult()).isEqualTo("IGNORED");
        assertThat(historyCapture.value.getFailReason()).isEqualTo("operation is already terminal or state has changed");
        verify(operationMapper, never()).completeStatusPhysical(anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any());
        verify(operationMapper, never()).updateNonTerminalChannelResultPhysical(anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), anyString(), any());
        verify(orderMapper, never()).completeStatusPhysical(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any(), any(), any(), any());
        verify(orderMapper, never()).markInitialSuccessPhysical(anyString(), anyString(), anyString(), any(BigDecimal.class), any());
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
        when(historyMapper.insertPhysical(anyString(), any(TransactionStatusHistoryDO.class))).thenAnswer(invocation -> {
            historyCapture.physicalTable = invocation.getArgument(0);
            historyCapture.value = invocation.getArgument(1);
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());
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
        assertThat(historyCapture.physicalTable).isEqualTo("transaction_status_history_202603");
        assertThat(historyCapture.value.getTransitionResult()).isEqualTo("IGNORED");
        assertThat(historyCapture.value.getFailReason()).isEqualTo("operation is already terminal");
        verify(operationMapper, never()).completeStatusPhysical(anyString(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any());
        verify(orderMapper, never()).markInitialSuccessPhysical(anyString(), anyString(), anyString(), any(BigDecimal.class), any());
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
        when(interactionLogMapper.insertPhysical(anyString(), any(TransactionChannelInteractionLogDO.class))).thenAnswer(invocation -> {
            interactionCapture.physicalTable = invocation.getArgument(0);
            interactionCapture.value = invocation.getArgument(1);
            return 1;
        });
        when(merchantApiLogMapper.insertPhysical(anyString(), any(TransactionMerchantApiInteractionLogDO.class))).thenAnswer(invocation -> {
            merchantApiCapture.physicalTable = invocation.getArgument(0);
            merchantApiCapture.value = invocation.getArgument(1);
            return 1;
        });
        when(notificationMapper.insertPhysical(anyString(), any(TransactionMerchantNotificationDO.class))).thenAnswer(invocation -> {
            notificationCapture.physicalTable = invocation.getArgument(0);
            notificationCapture.value = invocation.getArgument(1);
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser());
        PaymentCreateCommandDTO commandDTO = baseCommand();
        commandDTO.setRequestId(commandDTO.getMerchantOrderId());
        commandDTO.setMerchantRequestCipherMasked("cipher***tail");
        commandDTO.setRequestFingerprint("request-digest");
        commandDTO.setMerchantRequestPlainJsonMasked("""
                {"orderInfo":{"orderNo":"M202607140001"},"cardInfo":{"cardNo":"512345******0008","securityCode":"***"}}
                """);
        commandDTO.setOpenApiRequestPath("/api/rest/payment/v1/authorization");
        commandDTO.setOpenApiRequestTime(LocalDateTime.of(2026, 7, 1, 0, 29, 59));
        commandDTO.setCallbackUrl("https://merchant.example/callback?token=secret");
        PaymentChannelInvokeResultDTO invokeResultDTO = channelInvokeResult();
        invokeResultDTO.getChannelResponse().getRawResponse().put("httpStatus", "200");
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

        assertThat(interactionCapture.physicalTable).isEqualTo("transaction_channel_interaction_log_202603");
        assertThat(interactionCapture.value.getInteractionType()).isEqualTo("REQUEST_RESPONSE");
        assertThat(interactionCapture.value.getRequestBodyJsonMasked()).contains("\"channelOrderNo\"");
        assertThat(interactionCapture.value.getResponseBodyJsonMasked()).contains("\"channelResponseCode\":\"00\"");
        assertThat(interactionCapture.value.getResponseBodyJsonMasked()).contains("\"httpStatus\":\"200\"");
        assertThat(interactionCapture.value.getDurationMillis()).isEqualTo(1000);
        assertThat(merchantApiCapture.physicalTable).isEqualTo("transaction_merchant_api_interaction_log_202603");
        assertNestedMerchantPayload(merchantApiCapture.value.getResponsePlainJsonMasked());
        assertThat(merchantApiCapture.value.getResponsePlainJsonMasked()).doesNotContain("\"merchantId\":\"200001\",");
        assertThat(merchantApiCapture.value.getResponsePlainJsonMasked()).doesNotContain("\"status\":\"SUCCESS\"");
        assertThat(merchantApiCapture.value.getResponsePlainJsonMasked()).doesNotContain("dccEnabled");
        assertThat(merchantApiCapture.value.getResponseCipherDigest()).isNull();
        assertThat(notificationCapture.physicalTable).isEqualTo("transaction_merchant_notification_202603");
        assertThat(notificationCapture.value.getTargetUrlMasked()).isEqualTo("https://merchant.example/callback?***");
        assertNestedMerchantPayload(notificationCapture.value.getPayloadJsonMasked());
    }

    private PaymentCreateCommandDTO baseCommand() {
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200001");
        commandDTO.setMerchantOrderNo("M202607140001");
        commandDTO.setPaymentMethod("BANK_CARD");
        commandDTO.setAmount(new BigDecimal("12.34"));
        commandDTO.setCurrency("USD");
        commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 7, 1, 0, 30));
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfoDTO = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfoDTO.setCardBrand("MASTERCARD");
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

    private PaymentQuarterShardingProperties shardingProperties() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        properties.getTables().put("transaction_order", tableRule("transaction_order"));
        properties.getTables().put("transaction_operation", tableRule("transaction_operation"));
        properties.getTables().put("transaction_status_history", tableRule("transaction_status_history"));
        properties.getTables().put("transaction_channel_request", tableRule("transaction_channel_request"));
        properties.getTables().put("transaction_channel_interaction_log", tableRule("transaction_channel_interaction_log"));
        properties.getTables().put("transaction_flow_event", tableRule("transaction_flow_event"));
        properties.getTables().put("transaction_amount_change_log", tableRule("transaction_amount_change_log"));
        properties.getTables().put("transaction_payment_method_info", tableRule("transaction_payment_method_info"));
        properties.getTables().put("transaction_merchant_notification", tableRule("transaction_merchant_notification"));
        properties.getTables().put("transaction_merchant_api_interaction_log", tableRule("transaction_merchant_api_interaction_log"));
        return properties;
    }

    private ShardingDataTemplate shardingDataTemplate() {
        ShardingTableRangeResolver rangeResolver = new ShardingTableRangeResolver(
                shardingProperties(),
                new ShardingQuarterResolver(),
                new ShardingPhysicalTableNameResolver());
        return new ShardingDataTemplate(rangeResolver);
    }

    private PaymentQuarterShardingProperties.TableRule tableRule(String logicalTable) {
        PaymentQuarterShardingProperties.TableRule tableRule = new PaymentQuarterShardingProperties.TableRule();
        tableRule.setLogicalTable(logicalTable);
        tableRule.setTemplateTable(logicalTable);
        tableRule.setStartYear(2026);
        tableRule.setStartQuarter(1);
        tableRule.setEndYear(2035);
        tableRule.setEndQuarter(4);
        tableRule.setTableNameFormat("%s_%d%02d");
        return tableRule;
    }

    private static class Captured<T> {

        private String physicalTable;

        private T value;
    }

    private static class CapturedList<T> {

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
