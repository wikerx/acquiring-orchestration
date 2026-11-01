package com.scott.payment.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Merchant 服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class MerchantApplication {

    /**
     * 启动商户服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MerchantApplication.class, args);
    }
}
