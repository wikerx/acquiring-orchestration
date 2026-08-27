package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.entity.ClearingPaymentMethodInfoDO;
import com.scott.payment.clearing.entity.ClearingTransactionLocatorDO;
import com.scott.payment.clearing.mapper.ClearingTransactionContextMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.FeeConfigurationSnapshotService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingPreparationServiceTest
 * @date : 2026-08-26 10:55
 * @email : scott_x@163.com
 * @description : 验证清分事务外准备使用 locator 的真实根分片时间，并只加载确切费用版本和非敏感支付维度。
 * @status : create
 */
class DefaultClearingPreparationServiceTest {

    @Test
    void preparationAndSnapshotEntryPointsShouldUseTransactionDataSource() throws NoSuchMethodException {
        assertThat(DefaultClearingPreparationService.class.getMethod(
                "prepare", PaymentTransactionEventMessage.class, ClearingClaimResult.class, String.class)
                .getAnnotation(DS.class))
                .isNotNull()
                .extracting(DS::value)
                .isEqualTo(DataSourceName.TRANSACTION);
        assertThat(DefaultClearingPreparationService.class.getMethod(
                "prepareForRecalculation", PaymentTransactionEventMessage.class, ClearingClaimResult.class,
                String.class, FeeVersionSnapshot.class).getAnnotation(DS.class))
                .isNotNull()
                .extracting(DS::value)
                .isEqualTo(DataSourceName.TRANSACTION);
        assertThat(DefaultFeeConfigurationSnapshotService.class.getMethod(
                "load", String.class, String.class, String.class, LocalDateTime.class).getAnnotation(DS.class))
                .isNotNull()
                .extracting(DS::value)
                .isEqualTo(DataSourceName.TRANSACTION);
    }

    @Test
    void prepareShouldUseRootPaymentMethodAndValidatedCurrentLocator() {
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingTransactionOperationMapper operationMapper = mock(ClearingTransactionOperationMapper.class);
        ClearingTransactionFinanceStateMapper financeStateMapper = mock(ClearingTransactionFinanceStateMapper.class);
        FeeConfigurationSnapshotService snapshotService = mock(FeeConfigurationSnapshotService.class);
        DefaultClearingPreparationService service = new DefaultClearingPreparationService(
                contextMapper, operationMapper, financeStateMapper, snapshotService);
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 9, 0);
        LocalDateTime rootTime = time.minusDays(1);
        ClearingOperationFacts operation = new ClearingOperationFacts(
                "TX-2", "OP-1", null, "M-1", "ORDER-1", "PAYMENT", "SUCCESS",
                "EUR", new BigDecimal("100.00"), "EUR", new BigDecimal("100.00"),
                "EUR", new BigDecimal("100.00"), 2, time, time.minusHours(8),
                "Asia/Shanghai", 2);
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.ACQUIRED, "FS-1", 0, 3, operation);
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG-1");
        message.setTransactionId("TX-2");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setTransactionDateTime(time);
        ClearingTransactionLocatorDO locator = locator("TX-2", "TX-ROOT", time, rootTime);
        ClearingPaymentMethodInfoDO method = new ClearingPaymentMethodInfoDO();
        method.setPaymentMethod("BANK_CARD");
        method.setPaymentBrand("VISA");
        method.setThreeDsIndicator("Y");
        FeeVersionSnapshot snapshot = mock(FeeVersionSnapshot.class);
        when(snapshot.reserve()).thenReturn(null);
        when(contextMapper.selectLocator("M-1", "TX-2")).thenReturn(locator);
        when(contextMapper.selectPaymentMethod("TX-ROOT", rootTime)).thenReturn(method);
        when(contextMapper.existsInternalRiskCall("TX-2", time)).thenReturn(true);
        when(snapshotService.load("M-1", "OP-1", "TX-2", time)).thenReturn(snapshot);

        CompletionCommand result = service.prepare(message, claim, "worker-1");

        assertThat(result.paymentType()).isEqualTo("BANK_CARD");
        assertThat(result.paymentMethod()).isEqualTo("VISA");
        assertThat(result.occurredRiskServices()).containsExactlyInAnyOrder("INTERNAL", "THREE_DS");
        assertThat(result.currentLocator().rootTransactionDateTime()).isEqualTo(rootTime);
    }

    private ClearingTransactionLocatorDO locator(String transactionId,
                                                  String rootTransactionId,
                                                  LocalDateTime transactionTime,
                                                  LocalDateTime rootTime) {
        ClearingTransactionLocatorDO row = new ClearingTransactionLocatorDO();
        row.setTransactionId(transactionId);
        row.setOperationId("OP-1");
        row.setRootTransactionId(rootTransactionId);
        row.setMerchantId("M-1");
        row.setMerchantOrderNo("ORDER-1");
        row.setTransactionType("PAYMENT");
        row.setTransactionDateTime(transactionTime);
        row.setRootTransactionDateTime(rootTime);
        return row;
    }
}
