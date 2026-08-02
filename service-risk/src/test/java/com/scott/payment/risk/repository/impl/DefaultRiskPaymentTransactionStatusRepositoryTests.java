package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.component.db.sharding.TransactionShardingMode;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.db.sharding.TransactionShardingRuntimeState;
import com.scott.payment.risk.domain.PaymentTransactionLookupResult;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskPaymentTransactionStatusRepositoryTests
 * @date : 2026-08-02 03:40
 * @email : scott_x@163.com
 * @description : 校验 Risk 强一致交易状态查询按交易号恢复单季度并只访问 ShardingSphere 逻辑表。
 * @status : create
 */
class DefaultRiskPaymentTransactionStatusRepositoryTests {

    @Test
    void shouldQueryLogicalTableWithinParsedQuarterInShardingSphereMode() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        TransactionShardingKeyParser shardingKeyParser = mock(TransactionShardingKeyParser.class);
        LocalDateTime parsedTime = LocalDateTime.of(2026, 8, 2, 3, 40);
        when(shardingKeyParser.parseTransactionDateTime("TX-2026-Q3")).thenReturn(parsedTime);
        when(mapper.selectPaymentTransactionStatus(
                "TX-2026-Q3",
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)))
                .thenReturn("SUCCESS");
        DefaultRiskPaymentTransactionStatusRepository repository =
                new DefaultRiskPaymentTransactionStatusRepository(
                        mapper,
                        shardingDataTemplate,
                        shardingKeyParser,
                        runtimeState(TransactionShardingMode.SHARDINGSPHERE));

        PaymentTransactionLookupResult result = repository.findStatus(" TX-2026-Q3 ");

        assertThat(result.availability()).isEqualTo(PaymentTransactionLookupResult.Availability.FOUND);
        assertThat(result.paymentStatus()).isEqualTo("SUCCESS");
        verify(mapper).selectPaymentTransactionStatus(
                "TX-2026-Q3",
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0));
        verifyNoInteractions(shardingDataTemplate);
    }

    @Test
    void shouldReturnUnknownWithoutDatabaseAccessWhenTransactionIdCannotRestoreQuarter() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        TransactionShardingKeyParser shardingKeyParser = mock(TransactionShardingKeyParser.class);
        DefaultRiskPaymentTransactionStatusRepository repository =
                new DefaultRiskPaymentTransactionStatusRepository(
                        mapper,
                        shardingDataTemplate,
                        shardingKeyParser,
                        runtimeState(TransactionShardingMode.SHARDINGSPHERE));

        PaymentTransactionLookupResult result = repository.findStatus("unroutable");

        assertThat(result.availability()).isEqualTo(PaymentTransactionLookupResult.Availability.UNKNOWN);
        verifyNoInteractions(mapper, shardingDataTemplate);
    }

    @Test
    void shouldCompareLogicalStatusButReturnLegacyStatusInCompareMode() {
        RiskRuntimeMapper mapper = mock(RiskRuntimeMapper.class);
        ShardingDataTemplate shardingDataTemplate = mock(ShardingDataTemplate.class);
        TransactionShardingKeyParser shardingKeyParser = mock(TransactionShardingKeyParser.class);
        LocalDateTime parsedTime = LocalDateTime.of(2026, 11, 2, 3, 40);
        when(shardingKeyParser.parseTransactionDateTime("TX-COMPARE")).thenReturn(parsedTime);
        when(shardingDataTemplate.<String>queryOne(any(), any())).thenReturn("PENDING");
        when(mapper.selectPaymentTransactionStatus(
                "TX-COMPARE",
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0)))
                .thenReturn("SUCCESS");
        DefaultRiskPaymentTransactionStatusRepository repository =
                new DefaultRiskPaymentTransactionStatusRepository(
                        mapper,
                        shardingDataTemplate,
                        shardingKeyParser,
                        runtimeState(TransactionShardingMode.COMPARE));

        PaymentTransactionLookupResult result = repository.findStatus("TX-COMPARE");

        assertThat(result.availability()).isEqualTo(PaymentTransactionLookupResult.Availability.FOUND);
        assertThat(result.paymentStatus()).isEqualTo("PENDING");
        verify(shardingDataTemplate).queryOne(any(), any());
        verify(mapper).selectPaymentTransactionStatus(
                "TX-COMPARE",
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0));
    }

    private TransactionShardingRuntimeState runtimeState(TransactionShardingMode mode) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setMode(mode);
        TransactionShardingRuntimeState runtimeState = new TransactionShardingRuntimeState();
        runtimeState.activate(properties);
        return runtimeState;
    }
}
