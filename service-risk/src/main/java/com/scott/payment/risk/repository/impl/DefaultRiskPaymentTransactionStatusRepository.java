package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.risk.domain.PaymentTransactionLookupResult;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import com.scott.payment.risk.repository.RiskPaymentTransactionStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskPaymentTransactionStatusRepository
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 按持久化业务时间范围查询 payment 动作状态。
 * @status : create
 */
@Slf4j
@Repository
public class DefaultRiskPaymentTransactionStatusRepository
        implements RiskPaymentTransactionStatusRepository {

    private final RiskRuntimeMapper mapper;

    /**
     * 创建交易逻辑表状态仓储。
     *
     * @param mapper 风控运行时 Mapper
     */
    public DefaultRiskPaymentTransactionStatusRepository(RiskRuntimeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PaymentTransactionLookupResult findStatus(String transactionId,
                                                     LocalDateTime beginTime,
                                                     LocalDateTime endTimeExclusive) {
        if (!StringUtils.hasText(transactionId)
                || beginTime == null
                || endTimeExclusive == null
                || !beginTime.isBefore(endTimeExclusive)) {
            return PaymentTransactionLookupResult.unknown();
        }
        String normalizedTransactionId = transactionId.trim();
        try {
            String status = selectLogicalStatus(normalizedTransactionId, beginTime, endTimeExclusive);
            return StringUtils.hasText(status)
                    ? PaymentTransactionLookupResult.found(status)
                    : PaymentTransactionLookupResult.absent();
        } catch (RuntimeException exception) {
            log.warn("event: RISK_PAYMENT_STATUS_LOOKUP_FAILED transactionId: {} exceptionType: {}",
                    normalizedTransactionId, exception.getClass().getSimpleName());
            return PaymentTransactionLookupResult.unknown();
        }
    }

    /** 从 primary 上的交易逻辑表受控业务周期读取状态。 */
    private String selectLogicalStatus(String transactionId,
                                       LocalDateTime beginTime,
                                       LocalDateTime endTimeExclusive) {
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            return mapper.selectPaymentTransactionStatus(
                    transactionId, beginTime, endTimeExclusive);
        }
    }
}
