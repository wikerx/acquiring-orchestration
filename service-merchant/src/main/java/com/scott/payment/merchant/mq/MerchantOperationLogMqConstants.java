package com.scott.payment.merchant.mq;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogMqConstants
 * @date : 2026-06-20 08:40
 * @email : scott_x@163.com
 * @description : 商户管理系统操作日志 MQ 常量
 * @status : create
 */
public final class MerchantOperationLogMqConstants {

    /**
     * 商户管理系统操作日志消费者分组。
     *
     * <p>当前 RocketMQ 监听注解不会稳定解析自定义配置占位符，
     * 因此这里使用与默认 Nacos 配置保持一致的固定分组值，避免启动时把占位符原样传给 RocketMQ。</p>
     */
    public static final String MERCHANT_OPERATION_LOG_CONSUMER_GROUP = "acquiring-merchant-operation-log-consumer-dev";

    /**
     * 禁止实例化常量类。
     */
    private MerchantOperationLogMqConstants() {
    }
}
