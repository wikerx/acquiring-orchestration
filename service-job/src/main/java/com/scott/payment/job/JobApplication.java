package com.scott.payment.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 任务应用
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class JobApplication {

    /**
     * 启动任务调度服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}
