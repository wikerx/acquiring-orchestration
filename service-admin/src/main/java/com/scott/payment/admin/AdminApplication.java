package com.scott.payment.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 管理端 Spring Boot 启动入口，用于运营后台，封装该业务的本地校验、转换或运行时协作入口。
 */
@SpringBootApplication(scanBasePackages = "com.scott.payment")
@EnableScheduling
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
