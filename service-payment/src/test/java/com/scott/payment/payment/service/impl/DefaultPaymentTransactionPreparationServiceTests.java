package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证首次交易本地准备在风控和 MID 路由前补齐服务端卡品牌。
 */
class DefaultPaymentTransactionPreparationServiceTests {

    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";
    private static final String IDEMPOTENCY_KEY = "payment-idempotency-key";

    @Test
    void shouldEnrichCardBrandBeforeRiskAndRouteProcessing() {
        PaymentCheckoutCardCapabilityService cardCapabilityService = mock(PaymentCheckoutCardCapabilityService.class);
        PaymentRiskInvokeService riskInvokeService = mock(PaymentRiskInvokeService.class);
        PaymentChannelRouteService routeService = mock(PaymentChannelRouteService.class);
        TransactionIdempotencyService idempotencyService = mock(TransactionIdempotencyService.class);
        when(idempotencyService.buildTransactionOperationKey("200045", "ORDER-ID-1", "PAYMENT"))
                .thenThrow(new IllegalStateException("stop after card brand enrichment"));
        DefaultPaymentTransactionPreparationService service = new DefaultPaymentTransactionPreparationService(
                mock(IsoDictionaryService.class),
                riskInvokeService,
                routeService,
                mock(PaymentExchangeRateService.class),
                idempotencyService,
                mock(TransactionEventOutboxService.class),
                mock(TransactionRecordService.class),
                cardCapabilityService);
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200045");
        commandDTO.setMerchantOrderId("ORDER-ID-1");

        assertThatThrownBy(() -> service.prepareInitialTransaction(commandDTO, "PAYMENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stop after card brand enrichment");

        verify(cardCapabilityService).enrichCardBrand(commandDTO);
        verifyNoInteractions(riskInvokeService, routeService);
    }

    @Test
    void shouldKeepRemotePlanningOutsideAndPersistFactsInsideShortTransaction() {
        PreparationFixture fixture = new PreparationFixture();
        fixture.allowFreshRequest();

        PaymentInitialPreparationResultDTO resultDTO = fixture.service.prepareInitialTransaction(
                fixture.command(), "PAYMENT");

        assertThat(resultDTO.isDuplicate()).isFalse();
        assertThat(resultDTO.isCallChannel()).isTrue();
        assertThat(fixture.transactionManager.commitCount).isEqualTo(1);
        assertThat(fixture.transactionManager.rollbackCount).isZero();
        verify(fixture.riskInvokeService, never()).cancelMerchantLimitReservation(any(), any(), anyString());
    }

    @Test
    void shouldReturnExistingDuplicateBeforeRiskAndTransactionStart() {
        PreparationFixture fixture = new PreparationFixture();
        TransactionIdempotencyDO existing = existingResult();
        when(fixture.idempotencyService.find(TRANSACTION_OPERATION_SCOPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        PaymentInitialPreparationResultDTO resultDTO = fixture.service.prepareInitialTransaction(
                fixture.command(), "PAYMENT");

        assertThat(resultDTO.isDuplicate()).isTrue();
        assertThat(resultDTO.getResultDTO().getTransactionId()).isEqualTo("TX-EXISTING");
        assertThat(fixture.transactionManager.beginCount).isZero();
        verifyNoInteractions(fixture.riskInvokeService, fixture.routeService, fixture.exchangeRateService);
    }

    @Test
    void shouldCancelNewRiskReservationWhenDatabaseDetectsConcurrentDuplicate() {
        PreparationFixture fixture = new PreparationFixture();
        TransactionIdempotencyDO existing = existingResult();
        when(fixture.idempotencyService.find(TRANSACTION_OPERATION_SCOPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(fixture.idempotencyService.tryBegin(any(TransactionIdempotencyDO.class)))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                    return false;
                });
        PaymentCreateCommandDTO commandDTO = fixture.command();

        PaymentInitialPreparationResultDTO resultDTO = fixture.service.prepareInitialTransaction(commandDTO, "PAYMENT");

        assertThat(resultDTO.isDuplicate()).isTrue();
        assertThat(fixture.transactionManager.commitCount).isEqualTo(1);
        verify(fixture.riskInvokeService, times(1)).cancelMerchantLimitReservation(
                eq(commandDTO), eq(fixture.riskDecisionDTO), eq("duplicate payment preparation"));
        verifyNoInteractions(fixture.recordService);
    }

    @Test
    void shouldCancelRiskReservationOnceWhenPersistenceRollsBack() {
        PreparationFixture fixture = new PreparationFixture();
        fixture.allowFreshRequest();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            throw new IllegalStateException("transaction facts can not be persisted");
        }).when(fixture.recordService).recordInitialTransaction(any(), any(), any(), any(), any(), anyInt());
        PaymentCreateCommandDTO commandDTO = fixture.command();

        assertThatThrownBy(() -> fixture.service.prepareInitialTransaction(commandDTO, "PAYMENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("transaction facts can not be persisted");

        assertThat(fixture.transactionManager.commitCount).isZero();
        assertThat(fixture.transactionManager.rollbackCount).isEqualTo(1);
        verify(fixture.riskInvokeService, times(1)).cancelMerchantLimitReservation(
                eq(commandDTO), eq(fixture.riskDecisionDTO), eq("payment local preparation rolled back"));
    }

    /**
     * 构建已有首次交易结果，用于验证事务前命中和数据库并发冲突两条重复请求路径。
     */
    private static TransactionIdempotencyDO existingResult() {
        TransactionIdempotencyDO existing = new TransactionIdempotencyDO();
        existing.setTransactionId("TX-EXISTING");
        existing.setOperationId("OP-EXISTING");
        existing.setMerchantOrderNo("ORDER-001");
        existing.setMerchantOrderId("ORDER-ID-001");
        existing.setTransactionType("PAYMENT");
        existing.setTransactionStatus("PROCESSING");
        return existing;
    }

    /**
     * 首次支付短事务测试夹具，所有外部规划调用和本地写入都会断言当前事务状态。
     */
    private static final class PreparationFixture {

        private final IsoDictionaryService isoDictionaryService = mock(IsoDictionaryService.class);
        private final PaymentRiskInvokeService riskInvokeService = mock(PaymentRiskInvokeService.class);
        private final PaymentChannelRouteService routeService = mock(PaymentChannelRouteService.class);
        private final PaymentExchangeRateService exchangeRateService = mock(PaymentExchangeRateService.class);
        private final TransactionIdempotencyService idempotencyService = mock(TransactionIdempotencyService.class);
        private final TransactionEventOutboxService outboxService = mock(TransactionEventOutboxService.class);
        private final TransactionRecordService recordService = mock(TransactionRecordService.class);
        private final PaymentCheckoutCardCapabilityService cardCapabilityService = mock(PaymentCheckoutCardCapabilityService.class);
        private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        private final PaymentRiskDecisionDTO riskDecisionDTO = reservedRiskDecision();
        private final DefaultPaymentTransactionPreparationService service;

        private PreparationFixture() {
            when(idempotencyService.buildTransactionOperationKey("200045", "ORDER-ID-001", "PAYMENT"))
                    .thenReturn(IDEMPOTENCY_KEY);
            when(idempotencyService.buildMerchantOrderFlowKey("200045", "ORDER-001"))
                    .thenReturn("merchant-order-flow-key");
            when(idempotencyService.newProcessingRecord(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                    any(LocalDateTime.class), anyString(), anyString(), any(LocalDateTime.class)))
                    .thenAnswer(invocation -> {
                        TransactionIdempotencyDO record = new TransactionIdempotencyDO();
                        record.setIdempotencyScope(invocation.getArgument(0));
                        record.setIdempotencyKey(invocation.getArgument(1));
                        return record;
                    });
            when(riskInvokeService.checkPreRoute(any(PaymentCreateCommandDTO.class)))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                        return riskDecisionDTO;
                    });
            when(routeService.route(any(PaymentCreateCommandDTO.class)))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                        PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
                        routeResultDTO.setChannelId(101L);
                        routeResultDTO.setMidConfigId(1001L);
                        routeResultDTO.setMidNo("TEST-MID");
                        routeResultDTO.setRequestedCurrency("EUR");
                        routeResultDTO.setRoutedCurrency("USD");
                        routeResultDTO.setEdcRequired(true);
                        return routeResultDTO;
                    });
            when(exchangeRateService.findTransactionRate(eq("EUR"), eq("USD"), any(LocalDateTime.class)))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                        PaymentExchangeRateDTO rateDTO = new PaymentExchangeRateDTO();
                        rateDTO.setRateId(1001L);
                        rateDTO.setRawRateId(901L);
                        rateDTO.setRuleId(801L);
                        rateDTO.setSourceCode("PLATFORM");
                        rateDTO.setBaseCurrency("EUR");
                        rateDTO.setQuoteCurrency("USD");
                        rateDTO.setFinalRate(new BigDecimal("1.10000000"));
                        rateDTO.setEffectiveTime(invocation.getArgument(2));
                        return Optional.of(rateDTO);
                    });
            when(isoDictionaryService.getCurrency("USD"))
                    .thenReturn(Optional.of(new IsoCurrencyInfo(
                            "USD", "840", "US Dollar", "美元", 2, 100L,
                            new BigDecimal("0.01"), "$")));
            when(isoDictionaryService.toMinorUnit(any(BigDecimal.class), eq("USD")))
                    .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0))
                            .movePointRight(2).longValueExact());
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                return null;
            }).when(recordService).recordInitialTransaction(any(), any(), any(), any(), any(), anyInt());
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                return null;
            }).when(outboxService).save(any());
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                return null;
            }).when(idempotencyService).complete(
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    any(BigDecimal.class), anyString(), anyString());
            service = new DefaultPaymentTransactionPreparationService(
                    isoDictionaryService,
                    riskInvokeService,
                    routeService,
                    exchangeRateService,
                    idempotencyService,
                    outboxService,
                    recordService,
                    cardCapabilityService,
                    transactionManager);
        }

        /**
         * 配置数据库幂等占位成功，并验证全部幂等写入都发生在短事务内。
         */
        private void allowFreshRequest() {
            when(idempotencyService.find(TRANSACTION_OPERATION_SCOPE, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.empty());
            when(idempotencyService.tryBegin(any(TransactionIdempotencyDO.class)))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                        return true;
                    });
        }

        /**
         * 创建包含跨币种换汇的首次支付命令。
         */
        private PaymentCreateCommandDTO command() {
            PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
            commandDTO.setMerchantId("200045");
            commandDTO.setMerchantOrderNo("ORDER-001");
            commandDTO.setMerchantOrderId("ORDER-ID-001");
            commandDTO.setTransactionType("PAYMENT");
            commandDTO.setPaymentMethod("BANK_CARD");
            commandDTO.setAmount(new BigDecimal("10.00"));
            commandDTO.setCurrency("EUR");
            commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 11, 4, 16, 30));
            return commandDTO;
        }

        /**
         * 构建已预占商户累计限额的风控通过结果。
         */
        private static PaymentRiskDecisionDTO reservedRiskDecision() {
            PaymentRiskDecisionDTO decisionDTO = PaymentRiskDecisionDTO.pass();
            decisionDTO.setRiskRecordNo("RISK-001");
            decisionDTO.setMerchantLimitReserved(true);
            return decisionDTO;
        }
    }

    /**
     * 最小本地事务管理器，用于验证 TransactionTemplate 的提交、回滚和同步回调行为。
     */
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private int beginCount;
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            beginCount++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }
    }
}
