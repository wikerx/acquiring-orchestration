package com.scott.payment.risk.repository.impl;

import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.repository.RiskAuditRecordPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * MQ 风控审计发布器。
 */
@Slf4j
@Service
public class MqRiskAuditRecordPublisher implements RiskAuditRecordPublisher {

    /**
     * 风控审计消息发布入口；骨架环境未装配时允许为空。
     */
    private final MqProducer mqProducer;

    /**
     * 风控审计 MQ 开关和消费幂等配置。
     */
    private final RiskEvaluationProperties properties;

    /**
     * 创建可选的风控审计 MQ 发布器。
     *
     * @param mqProducerProvider MQ 生产者提供器
     * @param properties         风控评估配置
     */
    public MqRiskAuditRecordPublisher(ObjectProvider<MqProducer> mqProducerProvider,
                                      RiskEvaluationProperties properties) {
        this.mqProducer = mqProducerProvider.getIfAvailable();
        this.properties = properties;
    }

    /**
     * 发布已脱敏的风控评估审计消息。
     * <p>
     * 审计 MQ 未启用或骨架环境缺少生产者时跳过；发布异常记录 riskRecordNo 后返回，不把
     * 非交易事实的异步审计故障传播到支付决策链路。
     * </p>
     *
     * @param message 风控评估审计消息
     */
    @Override
    public void publish(RiskEvaluationAuditMessage message) {
        if (!properties.isAuditMqEnabled() || mqProducer == null || message == null
                || !StringUtils.hasText(message.getRiskRecordNo())) {
            return;
        }
        try {
            mqProducer.send(MqTopic.RISK_EVALUATION_AUDIT,
                    MqTag.RISK_EVALUATION_AUDIT,
                    message);
        } catch (RuntimeException exception) {
            log.warn("event: RISK_AUDIT_PUBLISH_FAILED riskRecordNo: {} reason: {}",
                    message.getRiskRecordNo(),
                    exception.getMessage());
        }
    }
}
