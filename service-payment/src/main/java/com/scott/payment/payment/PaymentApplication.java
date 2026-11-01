package com.scott.payment.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * 收单支付核心服务启动入口，启用交易编排、渠道执行和后台补偿调度。
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
