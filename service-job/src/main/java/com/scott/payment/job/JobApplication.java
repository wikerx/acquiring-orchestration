package com.scott.payment.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobApplication
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务应用
 * @status : create
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
