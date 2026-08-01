package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogMessageSanitizer
 * @date : 2026-06-20 22:59
 * @email : scott_x@163.com
 * @description : 操作日志 MQ 消息正文二次截断器
 * @status : create
 */
public class OperationLogMessageSanitizer {

    /**
     * 操作日志 MQ 配置。
     */
    private final OperationLogMqProperties properties;

    /**
     * 创建消息正文截断器。
     *
     * @param properties 操作日志 MQ 配置
     */
    public OperationLogMessageSanitizer(OperationLogMqProperties properties) {
        this.properties = properties;
    }

    /**
     * 按最大长度截断日志正文。
     *
     * @param value 原始正文
     * @return 截断后的正文
     */
    public String sanitize(String value) {
        return sanitize(value, properties.getMaxMessageLength());
    }

    /**
     * 按指定字段契约截断日志文本。
     *
     * @param value     原始正文
     * @param maxLength 字段允许的最大字符数
     * @return 截断后的正文
     */
    public String sanitize(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
