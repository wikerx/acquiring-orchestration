package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.component.db.sharding.TransactionShardingRuntimeState;
import com.scott.payment.risk.domain.PaymentTransactionLookupResult;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import com.scott.payment.risk.repository.RiskPaymentTransactionStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final TransactionShardingRuntimeState transactionShardingRuntimeState;

    public DefaultRiskPaymentTransactionStatusRepository(
            RiskRuntimeMapper mapper,
            ShardingDataTemplate shardingDataTemplate,
            TransactionShardingKeyParser shardingKeyParser) {
        this(mapper, shardingDataTemplate, shardingKeyParser, new TransactionShardingRuntimeState());
    }

    /**
     * 创建支持交易逻辑表只读切换的状态仓储。
     *
     * @param mapper 风控运行时 Mapper
     * @param shardingDataTemplate Legacy 分表访问入口
     * @param shardingKeyParser 交易号季度解析器
     * @param transactionShardingRuntimeState 当前实例交易分片模式
     */
    @Autowired
    public DefaultRiskPaymentTransactionStatusRepository(
            RiskRuntimeMapper mapper,
            ShardingDataTemplate shardingDataTemplate,
            TransactionShardingKeyParser shardingKeyParser,
            TransactionShardingRuntimeState transactionShardingRuntimeState) {
        this.mapper = mapper;
        this.shardingDataTemplate = shardingDataTemplate;
        this.shardingKeyParser = shardingKeyParser;
        this.transactionShardingRuntimeState = transactionShardingRuntimeState;
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
        String normalizedTransactionId = transactionId.trim();
        try {
            String status;
            if (transactionShardingRuntimeState.isShardingWriteEnabled()) {
                status = selectLogicalStatus(normalizedTransactionId, transactionTime);
            } else {
                status = selectLegacyStatus(normalizedTransactionId, transactionTime);
                if (transactionShardingRuntimeState.isReadComparisonEnabled()) {
                    compareLogicalStatus(normalizedTransactionId, transactionTime, status);
                }
            }
            return StringUtils.hasText(status)
                    ? PaymentTransactionLookupResult.found(status)
                    : PaymentTransactionLookupResult.absent();
        } catch (RuntimeException exception) {
            log.warn("event: RISK_PAYMENT_STATUS_LOOKUP_FAILED transactionId: {} exceptionType: {}",
                    normalizedTransactionId, exception.getClass().getSimpleName());
            return PaymentTransactionLookupResult.unknown();
        }
    }

    /** 从 Legacy 物理表路径读取交易状态。 */
    private String selectLegacyStatus(String transactionId, LocalDateTime transactionTime) {
        return shardingDataTemplate.queryOne(
                ShardingSingleTableContext.of(
                        TRANSACTION_OPERATION_TABLE,
                        transactionTime,
                        DataSourceName.MASTER),
                table -> mapper.selectPaymentTransactionStatusPhysical(table, transactionId));
    }

    /** 从 primary 上的交易逻辑表单季度读取最新状态。 */
    private String selectLogicalStatus(String transactionId, LocalDateTime transactionTime) {
        LocalDateTime beginTime = quarterBegin(transactionTime);
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            return mapper.selectPaymentTransactionStatus(
                    transactionId, beginTime, beginTime.plusMonths(3));
        }
    }

    /** COMPARE 模式仅记录新旧只读结果差异，不改变在线返回结果。 */
    private void compareLogicalStatus(String transactionId,
                                      LocalDateTime transactionTime,
                                      String legacyStatus) {
        try {
            String logicalStatus = selectLogicalStatus(transactionId, transactionTime);
            if (!java.util.Objects.equals(legacyStatus, logicalStatus)) {
                log.warn("event: RISK_PAYMENT_STATUS_SHARDING_COMPARE_MISMATCH transactionId: {} legacyStatus: {} logicalStatus: {}",
                        transactionId, legacyStatus, logicalStatus);
            }
        } catch (RuntimeException exception) {
            log.warn("event: RISK_PAYMENT_STATUS_SHARDING_COMPARE_FAILED transactionId: {} exceptionType: {}",
                    transactionId, exception.getClass().getSimpleName());
        }
    }

    /** 返回交易号时间所在季度的半开范围起点。 */
    private LocalDateTime quarterBegin(LocalDateTime transactionTime) {
        int firstMonth = ((transactionTime.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(transactionTime.getYear(), firstMonth, 1, 0, 0);
    }
}
