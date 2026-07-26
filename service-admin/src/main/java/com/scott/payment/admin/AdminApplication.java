package com.scott.payment.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.scott.payment")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminApplication
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : AdminApplication Spring Boot 启动入口，用于装配当前服务的组件扫描、配置加载和运行时上下文，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
