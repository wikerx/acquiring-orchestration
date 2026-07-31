package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.risk.domain.PaymentTransactionLookupResult;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import com.scott.payment.risk.repository.RiskPaymentTransactionStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 按 transactionId 时间片定位 payment 动作分表的状态查询。
 */
@Slf4j
@Repository
public class DefaultRiskPaymentTransactionStatusRepository
        implements RiskPaymentTransactionStatusRepository {

    private static final String TRANSACTION_OPERATION_TABLE = "transaction_operation";

    private final RiskRuntimeMapper mapper;

    private final ShardingDataTemplate shardingDataTemplate;

    private final TransactionShardingKeyParser shardingKeyParser;

    public DefaultRiskPaymentTransactionStatusRepository(
            RiskRuntimeMapper mapper,
            ShardingDataTemplate shardingDataTemplate,
            TransactionShardingKeyParser shardingKeyParser) {
        this.mapper = mapper;
        this.shardingDataTemplate = shardingDataTemplate;
        this.shardingKeyParser = shardingKeyParser;
    }

    @Override
    public PaymentTransactionLookupResult findStatus(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            return PaymentTransactionLookupResult.unknown();
        }
        LocalDateTime transactionTime =
                shardingKeyParser.parseTransactionDateTime(transactionId.trim());
        if (transactionTime == null) {
            return PaymentTransactionLookupResult.unknown();
        }
        try {
            String status = shardingDataTemplate.queryOne(
                    ShardingSingleTableContext.of(
                            TRANSACTION_OPERATION_TABLE,
                            transactionTime,
                            DataSourceName.MASTER),
                    table -> mapper.selectPaymentTransactionStatus(
                            table,
                            transactionId.trim()));
            return StringUtils.hasText(status)
                    ? PaymentTransactionLookupResult.found(status)
                    : PaymentTransactionLookupResult.absent();
        } catch (RuntimeException exception) {
            log.warn("event: RISK_PAYMENT_STATUS_LOOKUP_FAILED transactionId: {} reason: {}",
                    transactionId, exception.getMessage());
            return PaymentTransactionLookupResult.unknown();
        }
    }
}
