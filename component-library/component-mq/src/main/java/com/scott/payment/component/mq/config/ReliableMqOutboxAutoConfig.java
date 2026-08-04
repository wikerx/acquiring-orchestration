package com.scott.payment.component.mq.config;

import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxAutoConfig
 * @date : 2026-08-02 22:20
 * @email : scott_x@163.com
 * @description : 注册可靠 MQ Outbox 配置并启用低频补偿调度，不负责建表或初始化 RocketMQ 资源
 * @status : create
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ReliableMqOutboxProperties.class, EmailDeliveryProperties.class})
public class ReliableMqOutboxAutoConfig {

    /**
     * 创建提交后即时 Relay 的独立线程池。
     *
     * <p>Outbox 已经持久化，队列饱和时调用方可退回定时 Relay；该线程池的首要职责是
     * 避免事务提交回调继续占用请求线程和原事务数据库连接。</p>
     *
     * @return 即时 Relay 线程池
     */
    @Bean(name = "reliableMqOutboxRelayExecutor")
    public ThreadPoolTaskExecutor reliableMqOutboxRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("mq-outbox-relay-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
