package com.scott.payment.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentApplication
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Payment 服务启动类
 * @status : create
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}

