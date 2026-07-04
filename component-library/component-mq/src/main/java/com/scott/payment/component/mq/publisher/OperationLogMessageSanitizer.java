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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public String sanitize(String value) {
        if (!StringUtils.hasText(value) || value.length() <= properties.getMaxMessageLength()) {
            return value;
        }
        return value.substring(0, properties.getMaxMessageLength());
    }
}
