package com.scott.payment.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(scanBasePackages = "com.scott.payment")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentApplication
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : PaymentApplication Spring Boot 启动入口，用于装配当前服务的组件扫描、配置加载和运行时上下文，位于 支付核心服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class PaymentApplication {

    /**
     * 启动收单支付核心服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
