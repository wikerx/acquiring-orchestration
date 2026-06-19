package com.scott.payment.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理后台服务启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class AdminApplication {

    /**
     * 启动后台管理服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
