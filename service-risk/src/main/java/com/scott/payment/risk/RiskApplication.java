package com.scott.payment.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 风控服务启动类，位于 service-risk 服务入口层，承载收单实时风控评估和后续规则引擎接入边界。
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
@EnableScheduling
public class RiskApplication {

    /**
     * 启动风控服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RiskApplication.class, args);
    }
}
