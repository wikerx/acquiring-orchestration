package com.scott.payment.component.mq.initializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * RocketMQ 资源初始化启动 Runner
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
    @Override
    public void run(ApplicationArguments args) {
        try {
            resourceInitializer.initialize();
        } catch (RuntimeException exception) {
            if (failFast) {
                throw exception;
            }
            log.error("RocketMQ 资源初始化失败并降级继续启动, event: ROCKET_MQ_RESOURCE_INITIALIZATION_FAILED, failFast: false, exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
