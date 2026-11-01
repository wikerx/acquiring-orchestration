package com.scott.payment.data.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.data.mapper.DataRiskAuditMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 风控评估审计事务写入服务，以 risk_record_no 唯一键提供 MQ 最终幂等
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
     * <p>主记录唯一键冲突后必须按 riskRecordNo 回查，确认同一评估此前已经完整提交才结束消费；
     * 其他约束冲突和数据库异常必须回滚主记录及明细，并由上层释放 Redis 辅助占用后触发 RocketMQ 重投。</p>
     *
     * @param message 已脱敏风控审计消息
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void persist(RiskEvaluationAuditMessage message) {
        LocalDateTime evaluationTime = message.getEvaluationTime() == null
                ? LocalDateTime.now() : message.getEvaluationTime();
        try {
            riskAuditMapper.insertEvaluationRecord(
                    message.getRiskRecordNo(),
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
        } catch (DuplicateKeyException exception) {
            // 防止其他唯一键冲突被误认成相同评估；只有 riskRecordNo 已存在才按幂等完成处理。
            if (existsByRiskRecordNo(message.getRiskRecordNo())) {
                return;
            }
            throw exception;
        }
        if (message.getHits() == null) {
            return;
        }
        for (RiskAuditHitMessage hit : message.getHits()) {
            if (isPersistable(hit)) {
                riskAuditMapper.insertEvaluationHit(message.getRiskRecordNo(), hit, evaluationTime);
            }
        }
    }

    /**
     * 在主库核对风控审计主记录是否已经完成提交。
     *
     * @param riskRecordNo 风控评估流水号
     * @return true 表示数据库中已经存在对应审计主记录
     */
    @DS(DataSourceName.MASTER)
    public boolean existsByRiskRecordNo(String riskRecordNo) {
        return StringUtils.hasText(riskRecordNo)
                && riskAuditMapper.countByRiskRecordNo(riskRecordNo.trim()) > 0;
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
