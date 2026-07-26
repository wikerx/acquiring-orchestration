package com.scott.payment.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantApplication
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Merchant 服务启动类
 * @status : create
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
