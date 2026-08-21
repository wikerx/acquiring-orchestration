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
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskAuditPersistenceService
 * @date : 2026-08-01 14:50
 * @email : scott_x@163.com
 * @description : 风控评估审计事务写入服务，以 risk_record_no 唯一键提供 MQ 最终幂等
 * @status : create
 */
@Service
public class RiskAuditPersistenceService {

    /** 风控审计数据访问入口。 */
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
     * <p>主记录唯一键冲突表示同一评估此前已经完整提交，直接结束消费；其他异常必须回滚主记录和明细，
     * 并由上层释放 Redis 辅助占用后触发 RocketMQ 重投。</p>
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
                    message.getDecisionReason(),
                    message.getHitCount() == null ? 0 : message.getHitCount(),
                    evaluationTime
            );
        } catch (DuplicateKeyException exception) {
            // 主记录唯一键命中表示相同 riskRecordNo 已完整提交，禁止重复写入命中明细。
            return;
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
