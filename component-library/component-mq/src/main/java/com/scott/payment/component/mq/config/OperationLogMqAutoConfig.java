package com.scott.payment.component.mq.config;

import com.scott.payment.component.mq.admin.RocketMqAdminFacade;
import com.scott.payment.component.mq.initializer.MqResourceInitializerRunner;
import com.scott.payment.component.mq.initializer.RocketMqResourceInitializer;
import com.scott.payment.component.mq.properties.MqResourceInitializerProperties;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.mq.publisher.OperationLogMessageSanitizer;
import com.scott.payment.component.mq.publisher.OperationLogTopicResolver;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogMqAutoConfig
 * @date : 2026-06-20 01:32
 * @email : scott_x@163.com
 * @description : 操作日志 MQ 自动配置入口
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogMqAutoConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Operation Log Mq Auto 配置，位于 component-library/component-mq 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties({OperationLogMqProperties.class, MqResourceInitializerProperties.class})
public class OperationLogMqAutoConfig {

    /**
     * 声明操作日志 Topic 解析器。
     *
     * @param properties 操作日志 MQ 配置
     * @return Topic 解析器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param properties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    public OperationLogTopicResolver operationLogTopicResolver(OperationLogMqProperties properties) {
        return new OperationLogTopicResolver(properties);
    }

    /**
     * 声明操作日志消息截断器。
     *
     * @param properties 操作日志 MQ 配置
     * @return 消息截断器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param properties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    public OperationLogMessageSanitizer operationLogMessageSanitizer(OperationLogMqProperties properties) {
        return new OperationLogMessageSanitizer(properties);
    }

    /**
     * 声明 RocketMQ Admin 门面。
     *
     * @param rocketMQProperties RocketMQ Starter 配置
     * @param initializerProperties 初始化器配置
     * @return RocketMQ Admin 门面
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param rocketMQProperties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param initializerProperties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    @ConditionalOnProperty(prefix = "acquiring.mq.initializer", name = "enabled", havingValue = "true")
    public RocketMqAdminFacade rocketMqAdminFacade(RocketMQProperties rocketMQProperties,
                                                   MqResourceInitializerProperties initializerProperties) {
        return new RocketMqAdminFacade(rocketMQProperties, initializerProperties);
    }

    /**
     * 声明 RocketMQ 资源初始化器。
     *
     * @param adminFacade 官方 Admin 门面
     * @param initializerProperties 初始化器配置
     * @return 资源初始化器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param adminFacade 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param initializerProperties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    @ConditionalOnProperty(prefix = "acquiring.mq.initializer", name = "enabled", havingValue = "true")
    public RocketMqResourceInitializer rocketMqResourceInitializer(RocketMqAdminFacade adminFacade,
                                                                   MqResourceInitializerProperties initializerProperties) {
        return new RocketMqResourceInitializer(adminFacade, initializerProperties);
    }

    /**
     * 声明资源初始化 Runner。
     *
     * @param resourceInitializer 资源初始化器
     * @param initializerProperties 初始化器配置
     * @return 启动 Runner
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param resourceInitializer 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param initializerProperties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    @ConditionalOnProperty(prefix = "acquiring.mq.initializer", name = "enabled", havingValue = "true")
    public MqResourceInitializerRunner mqResourceInitializerRunner(RocketMqResourceInitializer resourceInitializer,
                                                                   MqResourceInitializerProperties initializerProperties) {
        return new MqResourceInitializerRunner(resourceInitializer, initializerProperties.isFailFast());
    }
}
