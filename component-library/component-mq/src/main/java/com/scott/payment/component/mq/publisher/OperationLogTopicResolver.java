package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.mq.enums.OperationLogSystemCode;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogTopicResolver
 * @date : 2026-06-20 22:58
 * @email : scott_x@163.com
 * @description : 操作日志 Topic 解析器
 * @status : create
 */
public class OperationLogTopicResolver {

    /**
     * 操作日志 MQ 配置。
     */
    private final OperationLogMqProperties properties;

    /**
     * 创建 Topic 解析器。
     *
     * @param properties 操作日志 MQ 配置
     */
    public OperationLogTopicResolver(OperationLogMqProperties properties) {
        this.properties = properties;
    }

    /**
     * 根据系统编码解析 Topic。
     *
     * @param systemCode 系统编码
     * @return Topic 名称
     */
    public String resolve(OperationLogSystemCode systemCode) {
        return systemCode == OperationLogSystemCode.MERCHANT
                ? properties.getMerchantTopic()
                : properties.getAdminTopic();
    }
}
