package com.scott.payment.risk.repository;

import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskAuditRecordPublisher
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控审计记录发布器。
 * @status : create
 */
public interface RiskAuditRecordPublisher {

    /**
     * 发布脱敏风控评估审计消息；持久化消费者通过风险记录号唯一约束保证最终幂等。
     *
     * @param message 风控评估审计消息
     */
    void publish(RiskEvaluationAuditMessage message);
}
