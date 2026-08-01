package com.scott.payment.risk.repository;

import com.scott.payment.risk.mq.message.RiskEvaluationAuditMessage;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskAuditRecordWriter
 * @date : 2026-07-28 10:00
 * @email : scott_x@163.com
 * @description : 定义风控评估审计的持久化事务边界，具体实现必须具备数据库最终幂等保护
 * @status : create
 */
public interface RiskAuditRecordWriter {

    /**
     * 持久化一条风控评估主记录及其命中明细。
     *
     * @param message 风控评估审计消息；业务记录号不能为空，敏感命中值必须脱敏
     */
    void write(RiskEvaluationAuditMessage message);
}
