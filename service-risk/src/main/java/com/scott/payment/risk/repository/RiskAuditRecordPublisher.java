package com.scott.payment.risk.repository;

import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;

/**
 * 风控审计记录发布器。
 */
public interface RiskAuditRecordPublisher {

    /**
     * 发布脱敏风控评估审计消息；持久化消费者通过风险记录号唯一约束保证最终幂等。
     *
     * @param message 风控评估审计消息
     */
    void publish(RiskEvaluationAuditMessage message);
}
