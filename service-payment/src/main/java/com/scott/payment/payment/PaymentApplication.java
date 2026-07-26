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
 * @description : Payment Application 协作组件，位于 支付核心服务，封装 paymentapplication 相关的校验、转换、持久化访问或运行时协作入口。
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
