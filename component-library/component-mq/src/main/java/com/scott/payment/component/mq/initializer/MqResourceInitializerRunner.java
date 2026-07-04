package com.scott.payment.component.mq.initializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqResourceInitializerRunner
 * @date : 2026-06-20 22:56
 * @email : scott_x@163.com
 * @description : RocketMQ 资源初始化启动 Runner
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqResourceInitializerRunner
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Mq Resource Initializer Runner，位于 component-library/component-mq 的消息消费层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Slf4j
public class MqResourceInitializerRunner implements ApplicationRunner {

    /**
     * 资源初始化器。
     */
    private final RocketMqResourceInitializer resourceInitializer;

    /**
     * 初始化失败是否中断启动。
     */
    private final boolean failFast;

    /**
     * 创建初始化 Runner。
     *
     * @param resourceInitializer 资源初始化器
     * @param failFast 是否中断启动
     */
    public MqResourceInitializerRunner(RocketMqResourceInitializer resourceInitializer,
                                       boolean failFast) {
        this.resourceInitializer = resourceInitializer;
        this.failFast = failFast;
    }

    /**
     * 应用启动后执行资源检查。
     *
     * @param args 启动参数
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param args 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            resourceInitializer.initialize();
        } catch (RuntimeException exception) {
            if (failFast) {
                throw exception;
            }
            log.error("RocketMQ 资源初始化失败，已按 failFast=false 降级继续启动，原因：{}",
                    exception.getMessage(),
                    exception);
        }
    }
}
