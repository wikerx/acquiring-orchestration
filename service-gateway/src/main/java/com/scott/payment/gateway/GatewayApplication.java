package com.scott.payment.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway 服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class GatewayApplication {

    /**
     * 启动统一网关服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
