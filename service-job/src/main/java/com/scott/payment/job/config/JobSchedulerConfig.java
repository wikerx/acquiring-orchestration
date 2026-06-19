package com.scott.payment.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerConfig
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务调度配置类
 * @status : create
 */

@Configuration
public class JobSchedulerConfig {

    /**
     * 注册任务调度配置属性 Bean，并显式指定 Bean 名称供 SpEL 调度表达式引用。
     *
     * @return 任务调度配置属性
     */
    @Bean("jobSchedulerProperties")
    @ConfigurationProperties(prefix = "job.scheduler")
    public JobSchedulerProperties jobSchedulerProperties() {
        return new JobSchedulerProperties();
    }

    /**
     * 注册任务执行线程池。
     *
     * @return 调度执行线程池
     */
    @Bean
    public ThreadPoolTaskExecutor jobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("job-exec-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 注册失败重试与延迟任务调度线程池。
     *
     * @return 延迟调度线程池
     */
    @Bean
    public ThreadPoolTaskScheduler jobDelayTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("job-delay-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
