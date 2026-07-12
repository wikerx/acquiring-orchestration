package com.scott.payment.component.mq.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogMqProperties
 * @date : 2026-06-20 01:31
 * @email : scott_x@163.com
 * @description : 操作日志 RocketMQ 配置属性
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogMqProperties
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Operation Log Mq 配置属性，位于 component-library/component-mq 的消息消费层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.operation-log.mq")
public class OperationLogMqProperties {

    /**
     * 是否启用操作日志 MQ 链路。
     */
    private boolean enabled = true;

    /**
     * 后台管理系统操作日志 Topic。
     */
    private String adminTopic = "acquiring_admin_operation_log_topic";

    /**
     * 商户管理系统操作日志 Topic。
     */
    private String merchantTopic = "acquiring_merchant_operation_log_topic";

    /**
     * 生产者分组。
     */
    private String producerGroup = "acquiring-operation-log-producer";

    /**
     * 后台管理系统消费者分组。
     */
    private String adminConsumerGroup = "acquiring-admin-operation-log-consumer-dev";

    /**
     * 商户管理系统消费者分组。
     */
    private String merchantConsumerGroup = "acquiring-merchant-operation-log-consumer-dev";

    /**
     * 发送超时，单位毫秒。
     */
    private int sendTimeoutMs = 3000;

    /**
     * 消息最大长度。
     */
    private int maxMessageLength = 8192;

    /**
     * 是否启用敏感字段脱敏开关标识。
     */
    private boolean maskSensitiveData = true;

    /**
     * 消费幂等有效期，单位秒。
     */
    private long consumeIdempotentTtlSeconds = 604800L;
}
