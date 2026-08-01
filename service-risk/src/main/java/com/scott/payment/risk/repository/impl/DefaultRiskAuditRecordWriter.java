package com.scott.payment.risk.repository.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.mapper.RiskRuntimeMapper;
import com.scott.payment.risk.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.risk.repository.RiskAuditRecordWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskAuditRecordWriter
 * @date : 2026-07-28 10:00
 * @email : scott_x@163.com
 * @description : 在主库事务内先写风控评估主记录再写命中明细，以 risk_record_no 唯一键承担 MQ 最终幂等
 * @status : create
 */
@Service
@DS(DataSourceName.MASTER)
public class DefaultRiskAuditRecordWriter implements RiskAuditRecordWriter {

    /**
     * 风控主库 Mapper；主记录唯一键和同事务明细写入共同提供 MQ 最终幂等。
     */
    private final RiskRuntimeMapper riskRuntimeMapper;

    /**
     * 创建风控审计数据库写入器。
     *
     * @param riskRuntimeMapperProvider 风控主库 Mapper 提供器；未装配时保留骨架环境兼容，不执行持久化
     */
    public DefaultRiskAuditRecordWriter(ObjectProvider<RiskRuntimeMapper> riskRuntimeMapperProvider) {
        this.riskRuntimeMapper = riskRuntimeMapperProvider.getIfAvailable();
    }

    /**
     * 在同一事务内写入风控评估主记录及其命中明细。
     *
     * <p>主记录唯一冲突表示相同 riskRecordNo 已完整提交，直接按 MQ 重复投递返回；
     * 其他数据库异常继续抛出，由消费者触发消息重试。</p>
     *
     * @param message 风控评估审计消息；命中值在进入本方法前必须已经脱敏
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void write(RiskEvaluationAuditMessage message) {
        if (riskRuntimeMapper == null || message == null || !StringUtils.hasText(message.getRiskRecordNo())) {
            return;
        }
        LocalDateTime evaluationTime = message.getEvaluationTime() == null ? LocalDateTime.now() : message.getEvaluationTime();
        try {
            riskRuntimeMapper.insertEvaluationRecord(
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
        } catch (DuplicateKeyException ignored) {
            // 主记录唯一键表示同一评估已完整提交，MQ 重投无需重复写入明细。
            return;
        }
        if (message.getHits() == null || message.getHits().isEmpty()) {
            return;
        }
        for (RiskListMatch hit : message.getHits()) {
            if (hit != null && StringUtils.hasText(hit.getModuleType()) && StringUtils.hasText(hit.getFunctionCode())) {
                riskRuntimeMapper.insertEvaluationHit(message.getRiskRecordNo(), hit, evaluationTime);
            }
        }
    }
}
