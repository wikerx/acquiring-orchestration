package com.scott.payment.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商户 OpenAPI 服务启动类，负责装配开放接口、安全切面、加解密拦截器和内部支付服务客户端。
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class OpenApiApplication {

    /**
     * 启动商户开放接口服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OpenApiApplication.class, args);
    }
}
