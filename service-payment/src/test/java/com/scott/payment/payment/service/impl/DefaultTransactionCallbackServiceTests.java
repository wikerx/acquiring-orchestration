package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.ShardingTableRangeResolver;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.ChannelTradeStatus;
import com.scott.payment.channel.payment.executor.PaymentChannelCallbackExecutor;
import com.scott.payment.channel.payment.mpgs.MpgsPaymentChannelCallbackHandler;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
     * MPGS 成功回调应通过 order.id 和 transaction.id 定位动作单，并调用交易记录服务推进成功终态。
     */
    @Test
    void shouldProcessMpgsApprovedCallbackByChannelOrderAndTransactionId() {
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        when(callbackMapper.insertPhysical(anyString(), any(TransactionChannelCallbackDO.class))).thenReturn(1);
        when(callbackMapper.updateProcessResultPhysical(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(recordService.findOperationByChannelTransaction("TX202607141000000000001", "CH202607141000000000001"))
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
                shardingDataTemplate(),
                new TransactionShardingKeyParser(),
                Optional.of(new PaymentChannelCallbackExecutor(new PaymentChannelCallbackRegistry(
                        Optional.of(List.of(new MpgsPaymentChannelCallbackHandler()))))));

        TransactionChannelCallbackResultDTO resultDTO = callbackService.recordChannelCallback(callbackCommand());

        assertThat(resultDTO.getCallbackStatus()).isEqualTo("PROCESSED");
        assertThat(resultDTO.getProcessResult()).isEqualTo("STATUS_CHANGED");
        assertThat(resultDTO.getTransactionId()).isEqualTo("TX202607141000000000001");
        verify(recordService).completeByChannelCallback(any(), any(), anyString(),
                eq(PaymentTransactionStatusEnum.SUCCESS.getCode()), any(), any(),
                eq("AUTHORIZED"), eq("00"), eq("Approved"));
        assertThat(eventOutboxService.eventDO).isNotNull();
        assertThat(eventOutboxService.eventDO.getEventType())
                .isEqualTo(TransactionMqConstants.TRANSACTION_CALLBACK_PROCESSED_TAG);
        assertThat(eventOutboxService.eventDO.getTransactionId()).isEqualTo("TX202607141000000000001");
    }

    /**
     * WorldPay 同一订单可能先回调 AUTHORISED 再回调 CAPTURED，幂等键必须区分原始状态，不能吞掉后续 captured 终态事件。
     */
    @Test
    void shouldNotTreatWorldPayCapturedAsDuplicateAfterAuthorisedCallback() {
        TransactionChannelCallbackMapper callbackMapper = mock(TransactionChannelCallbackMapper.class);
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        when(callbackMapper.insertPhysical(anyString(), any(TransactionChannelCallbackDO.class))).thenReturn(1);
        when(callbackMapper.updateProcessResultPhysical(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        TransactionOperationDO operationDO = operation();
        operationDO.setTransactionType(PaymentTransactionTypeEnum.PAYMENT.getCode());
        when(recordService.findOperationByChannelTransaction("TX202607141000000000001", "CH202607141000000000001"))
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
                shardingDataTemplate(),
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
                  "order": {"id": "TX202607141000000000001", "status": "AUTHORIZED"},
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
        operationDO.setTransactionId("TX202607141000000000001");
        operationDO.setChannelOrderNo("TX202607141000000000001");
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
        orderDO.setRootTransactionId("TX202607141000000000001");
        orderDO.setLatestTransactionId("TX202607141000000000001");
        orderDO.setTransactionType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
        orderDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        orderDO.setTransactionCurrency("USD");
        orderDO.setTransactionAmount(new BigDecimal("12.34"));
        orderDO.setCurrencyExponent(2);
        orderDO.setTransactionDateTime(LocalDateTime.of(2026, 7, 14, 10, 0));
        orderDO.setVersion(0);
        return orderDO;
    }

    private PaymentQuarterShardingProperties shardingProperties() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        properties.getTables().put("transaction_channel_callback", tableRule("transaction_channel_callback"));
        properties.getTables().put("transaction_channel_callback_log", tableRule("transaction_channel_callback_log"));
        return properties;
    }

    private ShardingDataTemplate shardingDataTemplate() {
        PaymentQuarterShardingProperties properties = shardingProperties();
        ShardingTableRangeResolver rangeResolver = new ShardingTableRangeResolver(
                properties,
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

    private static class CapturingEventOutboxService implements TransactionEventOutboxService {

        /**
         * event DO 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private TransactionEventOutboxDO eventDO;

        @Override
        public void save(TransactionEventOutboxDO eventDO) {
            this.eventDO = eventDO;
        }

        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return List.of();
        }

        @Override
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            return true;
        }

        @Override
        public boolean markFailed(TransactionEventOutboxDO eventDO,
                                  LocalDateTime nextRetryTime,
                                  String failReason,
                                  LocalDateTime now) {
            return true;
        }
    }

    private static class FixedWorldPayCallbackHandler implements PaymentChannelCallbackHandler {

        @Override
        public String channelCode() {
            return "WPGXML";
        }

        @Override
        public ChannelCallbackResult handle(ChannelCallbackRequest request) {
            ChannelCallbackResult result = new ChannelCallbackResult();
            result.setChannelCode("WPGXML");
            result.setChannelOrderNo("TX202607141000000000001");
            result.setChannelTransactionId("CH202607141000000000001");
            result.setRawChannelStatus(request.getBody());
            result.setChannelTradeStatus(ChannelTradeStatus.SUCCESS.getCode());
            result.setChannelResponseCode(request.getBody());
            result.setChannelResponseMessage(request.getBody());
            return result;
        }
    }
}
