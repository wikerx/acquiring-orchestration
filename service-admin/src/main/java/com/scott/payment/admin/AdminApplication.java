package com.scott.payment.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminApplication
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 支付框架后台管理服务启动入口
 * @status : create
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
