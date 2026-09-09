package com.scott.payment.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentApplication
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : 支付application协作组件，位于 支付核心服务，封装该业务的本地校验、转换或运行时协作入口。
 * @status : create
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
@EnableScheduling
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
