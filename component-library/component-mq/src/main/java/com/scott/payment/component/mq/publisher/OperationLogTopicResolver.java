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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param systemCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public String resolve(OperationLogSystemCode systemCode) {
        return systemCode == OperationLogSystemCode.MERCHANT
                ? properties.getMerchantTopic()
                : properties.getAdminTopic();
    }
}
