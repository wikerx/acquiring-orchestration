package com.scott.payment.data.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.data.mapper.DataRiskAuditMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 风控评估审计事务写入服务，以非分片定位表的 risk_record_no 唯一键提供跨季度 MQ 最终幂等。
 */
@Service
public class RiskAuditPersistenceService {

    /**
     * 风控审计数据访问入口。
     */
    private final DataRiskAuditMapper riskAuditMapper;

    /**
     * 创建风控评估审计事务写入服务。
     *
     * @param riskAuditMapper 风控审计 Mapper
     */
    public RiskAuditPersistenceService(DataRiskAuditMapper riskAuditMapper) {
        this.riskAuditMapper = riskAuditMapper;
    }

    /**
     * 在同一主库事务内写入评估主记录和全部有效明细。
     *
     * <p>定位记录、主记录和执行明细使用同一 riskAudit 本地事务。定位唯一键冲突后必须按
     * riskRecordNo 和已保存的评估时间回查分片主记录，确认此前已经完整提交才结束消费；其他异常
     * 必须整体回滚，并由上层释放 Redis 辅助占用后触发 RocketMQ 重投。</p>
     *
     * @param message 已脱敏风控审计消息
     */
    @DS(DataSourceName.RISK_AUDIT)
    @Transactional(rollbackFor = Exception.class)
    public void persist(RiskEvaluationAuditMessage message) {
        if (message == null || !StringUtils.hasText(message.getRiskRecordNo())
                || message.getEvaluationTime() == null) {
            throw new IllegalArgumentException("risk audit record number and evaluation time are required");
        }
        String riskRecordNo = message.getRiskRecordNo().trim();
        LocalDateTime evaluationTime = message.getEvaluationTime();
        try {
            riskAuditMapper.insertEvaluationLocator(riskRecordNo, evaluationTime);
        } catch (DuplicateKeyException exception) {
            // 只有定位记录和对应季度主事实同时存在时，才把唯一键冲突视为已完成的重复消费。
            if (existsByRiskRecordNo(riskRecordNo)) {
                return;
            }
            throw exception;
        }
        riskAuditMapper.insertEvaluationRecord(
                riskRecordNo,
                message.getMerchantId(),
                message.getMerchantOrderNo(),
                message.getPaymentOrderNo(),
                message.getTransactionAmount(),
                message.getTransactionCurrency(),
                message.getRiskLevel(),
                message.getDecisionResult(),
                message.getDecisionReasonCode(),
                message.getDecisionReason(),
                message.getHitCount() == null ? 0 : message.getHitCount(),
                evaluationTime
        );
        if (message.getHits() == null) {
            return;
        }
        for (RiskAuditHitMessage hit : message.getHits()) {
            if (isPersistable(hit)) {
                riskAuditMapper.insertEvaluationHit(riskRecordNo, hit, evaluationTime, evaluationTime);
            }
        }
    }

    /**
     * 在主库核对风控审计主记录是否已经完成提交。
     *
     * @param riskRecordNo 风控评估流水号
     * @return true 表示数据库中已经存在对应审计主记录
     */
    @DS(DataSourceName.RISK_AUDIT)
    public boolean existsByRiskRecordNo(String riskRecordNo) {
        if (!StringUtils.hasText(riskRecordNo)) {
            return false;
        }
        String normalizedRecordNo = riskRecordNo.trim();
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            LocalDateTime evaluationTime = riskAuditMapper.selectEvaluationTime(normalizedRecordNo);
            return evaluationTime != null
                    && riskAuditMapper.countByRiskRecordNo(normalizedRecordNo, evaluationTime) > 0;
        }
    }

    /**
     * 判断命中明细是否具备稳定的模块和功能标识。
     *
     * @param hit MQ 命中明细
     * @return true 表示可以持久化
     */
    private boolean isPersistable(RiskAuditHitMessage hit) {
        return hit != null
                && StringUtils.hasText(hit.getModuleType())
                && StringUtils.hasText(hit.getFunctionCode());
    }
}
