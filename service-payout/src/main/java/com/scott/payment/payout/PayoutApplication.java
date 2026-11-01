package com.scott.payment.payout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 代付核心服务启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class PayoutApplication {

    /**
     * 启动代付核心服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PayoutApplication.class, args);
    }
}
