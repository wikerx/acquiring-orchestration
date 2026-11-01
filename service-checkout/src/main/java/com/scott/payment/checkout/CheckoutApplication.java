package com.scott.payment.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动托管收银台展示配置服务。
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class CheckoutApplication {

    /**
     * 启动收银台服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }
}
