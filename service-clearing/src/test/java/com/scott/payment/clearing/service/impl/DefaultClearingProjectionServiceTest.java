package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.LocatorFacts;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.entity.ClearingTransactionLocatorDO;
import com.scott.payment.clearing.entity.ClearingTransactionOrderDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingTransactionContextMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingProjectionServiceTest
 * @date : 2026-08-26 18:45
 * @email : scott_x@163.com
 * @description : 验证成功和失败清分事务使用动作分片 CAS、根主单行锁及统一生命周期聚合更新查询投影。
 * @status : create
 */
@SuppressWarnings("unchecked")
class DefaultClearingProjectionServiceTest {

    private static final LocalDateTime ROOT_TIME = LocalDateTime.of(2026, 8, 20, 8, 0);
    private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 8, 26, 8, 30);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 9, 0);

    private ClearingTransactionOperationMapper operationMapper;
    private ClearingTransactionContextMapper contextMapper;
    private DefaultClearingProjectionService service;

    @BeforeEach
    void setUp() {
        operationMapper = mock(ClearingTransactionOperationMapper.class);
        contextMapper = mock(ClearingTransactionContextMapper.class);
        service = new DefaultClearingProjectionService(operationMapper, contextMapper);
    }

    @Test
    void completedActionShouldProducePartialLifecycleWhenAnotherActionIsPending() {
        ClearingOperationFacts operation = operation();
        LocatorFacts current = currentLocator();
        when(operationMapper.updateClearingProjection(
                "TX-2", TRANSACTION_TIME, 3, "CLEARED", NOW, null, NOW)).thenReturn(1);
        when(contextMapper.selectOperationLocators("M-1", "OP-1"))
                .thenReturn(List.of(locatorRow("TX-1", ROOT_TIME), locatorRow("TX-2", TRANSACTION_TIME)));
        when(contextMapper.selectOperationClearingStatuses(any()))
                .thenReturn(List.of("PENDING", "CLEARED"));
        when(contextMapper.selectOrderForUpdate("OP-1", ROOT_TIME)).thenReturn(order());
        when(contextMapper.updateOrderClearingProjection("OP-1", ROOT_TIME, 7,
                "PARTIALLY_CLEARED", NOW)).thenReturn(1);

        service.updateWithLocator(operation, current, ClearingStateEnum.CLEARED, null, NOW);

        ArgumentCaptor<List<LocatorFacts>> locators = ArgumentCaptor.forClass(List.class);
        verify(contextMapper).selectOperationClearingStatuses(locators.capture());
        assertThat(locators.getValue()).extracting(LocatorFacts::transactionDateTime)
                .containsExactly(ROOT_TIME, TRANSACTION_TIME);
        verify(contextMapper).selectOrderForUpdate("OP-1", ROOT_TIME);
        verify(contextMapper).updateOrderClearingProjection(
                "OP-1", ROOT_TIME, 7, "PARTIALLY_CLEARED", NOW);
    }

    @Test
    void manualReviewShouldResolveLocatorAndProjectFailureToActionAndLifecycle() {
        ClearingOperationFacts operation = operation();
        when(contextMapper.selectLocator("M-1", "TX-2"))
                .thenReturn(locatorRow("TX-2", TRANSACTION_TIME));
        when(operationMapper.updateClearingProjection(
                "TX-2", TRANSACTION_TIME, 3, "FAILED", null,
                "FEE_SNAPSHOT_HASH_MISMATCH", NOW)).thenReturn(1);
        when(contextMapper.selectOperationLocators("M-1", "OP-1"))
                .thenReturn(List.of(locatorRow("TX-1", ROOT_TIME), locatorRow("TX-2", TRANSACTION_TIME)));
        when(contextMapper.selectOperationClearingStatuses(any()))
                .thenReturn(List.of("CLEARED", "FAILED"));
        when(contextMapper.selectOrderForUpdate("OP-1", ROOT_TIME)).thenReturn(order());
        when(contextMapper.updateOrderClearingProjection(
                "OP-1", ROOT_TIME, 7, "FAILED", NOW)).thenReturn(1);

        service.updateResolvingLocator(operation, ClearingStateEnum.MANUAL_REVIEW,
                "FEE_SNAPSHOT_HASH_MISMATCH", NOW);

        verify(operationMapper).updateClearingProjection(
                "TX-2", TRANSACTION_TIME, 3, "FAILED", null,
                "FEE_SNAPSHOT_HASH_MISMATCH", NOW);
        verify(contextMapper).updateOrderClearingProjection("OP-1", ROOT_TIME, 7, "FAILED", NOW);
    }

    @Test
    void incompleteLifecycleProjectionShouldFailBeforeOrderMutation() {
        when(operationMapper.updateClearingProjection(
                "TX-2", TRANSACTION_TIME, 3, "PENDING", null,
                "SOURCE_CLEARING_PENDING", NOW)).thenReturn(1);
        when(contextMapper.selectOperationLocators("M-1", "OP-1"))
                .thenReturn(List.of(locatorRow("TX-1", ROOT_TIME), locatorRow("TX-2", TRANSACTION_TIME)));
        when(contextMapper.selectOperationClearingStatuses(any())).thenReturn(List.of("PENDING"));

        assertThatExceptionOfType(ClearingProcessingException.class)
                .isThrownBy(() -> service.updateWithLocator(
                        operation(), currentLocator(), ClearingStateEnum.WAITING_SOURCE,
                        "SOURCE_CLEARING_PENDING", NOW))
                .satisfies(exception -> assertThat(exception.getFailureCode())
                        .isEqualTo(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT));
    }

    private ClearingOperationFacts operation() {
        return new ClearingOperationFacts(
                "TX-2", "OP-1", "TX-1", "M-1", "ORDER-1", "REFUND", "SUCCESS",
                "USD", new BigDecimal("20.00"), "USD", new BigDecimal("20.00"),
                "USD", new BigDecimal("20.00"), 2, TRANSACTION_TIME,
                TRANSACTION_TIME.minusHours(8), "Asia/Shanghai", 3);
    }

    private LocatorFacts currentLocator() {
        return new LocatorFacts(
                "TX-2", "OP-1", "TX-1", "M-1", "ORDER-1", "REFUND",
                TRANSACTION_TIME, ROOT_TIME);
    }

    private ClearingTransactionLocatorDO locatorRow(String transactionId, LocalDateTime transactionTime) {
        ClearingTransactionLocatorDO row = new ClearingTransactionLocatorDO();
        row.setTransactionId(transactionId);
        row.setOperationId("OP-1");
        row.setRootTransactionId("TX-1");
        row.setMerchantId("M-1");
        row.setMerchantOrderNo("ORDER-1");
        row.setTransactionType(transactionId.equals("TX-1") ? "PAYMENT" : "REFUND");
        row.setTransactionDateTime(transactionTime);
        row.setRootTransactionDateTime(ROOT_TIME);
        return row;
    }

    private ClearingTransactionOrderDO order() {
        ClearingTransactionOrderDO row = new ClearingTransactionOrderDO();
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setTransactionDateTime(ROOT_TIME);
        row.setVersion(7);
        return row;
    }
}
