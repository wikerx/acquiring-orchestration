package com.scott.payment.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentApplication
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付核心服务启动类，负责装配 service-payment 及共享组件 Bean，不承载交易业务逻辑。
 * @status : create
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
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
