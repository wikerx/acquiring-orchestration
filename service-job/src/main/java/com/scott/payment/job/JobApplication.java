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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobApplication
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Application，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param args 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}
