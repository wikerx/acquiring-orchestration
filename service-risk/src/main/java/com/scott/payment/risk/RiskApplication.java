package com.scott.payment.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskApplication
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控服务启动类，位于 service-risk 服务入口层，承载收单实时风控评估和后续规则引擎接入边界。
 * @status : create
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
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
