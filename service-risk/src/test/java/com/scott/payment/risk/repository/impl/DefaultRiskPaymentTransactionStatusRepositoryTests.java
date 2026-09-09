package com.scott.payment.risk.repository.impl;

import com.scott.payment.risk.domain.PaymentTransactionLookupResult;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskPaymentTransactionStatusRepositoryTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 校验 Risk 使用持久化业务时间范围查询 ShardingSphere 交易逻辑表。
 * @status : create
 */
class DefaultRiskPaymentTransactionStatusRepositoryTests {

    @Test
    void shouldQueryLogicalTableWithinPersistedBusinessPeriod() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        LocalDateTime beginTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(mapper.selectPaymentTransactionStatus("TX-2026-Q3", beginTime, endTime))
                .thenReturn("SUCCESS");
        DefaultRiskPaymentTransactionStatusRepository repository =
                new DefaultRiskPaymentTransactionStatusRepository(mapper);

        PaymentTransactionLookupResult result = repository.findStatus(
                " TX-2026-Q3 ", beginTime, endTime);

        assertThat(result.availability())
                .isEqualTo(PaymentTransactionLookupResult.Availability.FOUND);
        assertThat(result.paymentStatus()).isEqualTo("SUCCESS");
        verify(mapper).selectPaymentTransactionStatus("TX-2026-Q3", beginTime, endTime);
    }

    @Test
    void shouldReturnAbsentWhenNoTransactionExistsInBusinessPeriod() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        LocalDateTime beginTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 8, 1, 0, 0);
        DefaultRiskPaymentTransactionStatusRepository repository =
                new DefaultRiskPaymentTransactionStatusRepository(mapper);

        PaymentTransactionLookupResult result = repository.findStatus(
                "TX-ABSENT", beginTime, endTime);

        assertThat(result.availability())
                .isEqualTo(PaymentTransactionLookupResult.Availability.ABSENT);
        verify(mapper).selectPaymentTransactionStatus("TX-ABSENT", beginTime, endTime);
    }

    @Test
    void shouldReturnUnknownWithoutDatabaseAccessForInvalidBusinessPeriod() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        DefaultRiskPaymentTransactionStatusRepository repository =
                new DefaultRiskPaymentTransactionStatusRepository(mapper);
        LocalDateTime beginTime = LocalDateTime.of(2026, 8, 1, 0, 0);

        PaymentTransactionLookupResult missingTime = repository.findStatus(
                "TX-INVALID", null, beginTime);
        PaymentTransactionLookupResult reversedTime = repository.findStatus(
                "TX-INVALID", beginTime, beginTime.minusSeconds(1));

        assertThat(missingTime.availability())
                .isEqualTo(PaymentTransactionLookupResult.Availability.UNKNOWN);
        assertThat(reversedTime.availability())
                .isEqualTo(PaymentTransactionLookupResult.Availability.UNKNOWN);
        verifyNoInteractions(mapper);
    }
}
