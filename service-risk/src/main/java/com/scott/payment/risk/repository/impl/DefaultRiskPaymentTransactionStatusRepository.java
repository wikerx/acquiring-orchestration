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
 * 按持久化业务时间范围查询 payment 动作状态。
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

    /**
     * 按交易持久化业务时间范围查询支付状态。
     *
     * <p>参数非法或数据库查询异常时返回 UNKNOWN，禁止对账流程把未知结果当失败释放额度。</p>
     *
     * @param transactionId 支付系统交易号
     * @param beginTime 查询区间起点，包含该时间
     * @param endTimeExclusive 查询区间终点，不包含该时间
     * @return 已找到、明确不存在或未知的查询结果
     */
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
            log.warn("支付交易状态查询失败, event: RISK_PAYMENT_STATUS_LOOKUP_FAILED, transactionId: {}, exceptionType: {}",
                    normalizedTransactionId, exception.getClass().getSimpleName());
            return PaymentTransactionLookupResult.unknown();
        }
    }

    /**
     * 从 primary 上的交易逻辑表受控业务周期读取状态。
     *
     * @param transactionId 支付系统交易号
     * @param beginTime 查询区间起点，包含该时间
     * @param endTimeExclusive 查询区间终点，不包含该时间
     * @return 最新支付操作状态；区间内不存在时返回空
     */
    private String selectLogicalStatus(String transactionId,
                                       LocalDateTime beginTime,
                                       LocalDateTime endTimeExclusive) {
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            return mapper.selectPaymentTransactionStatus(
                    transactionId, beginTime, endTimeExclusive);
        }
    }
}
