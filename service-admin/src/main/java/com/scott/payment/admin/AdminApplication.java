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
 * @description : Admin Application 协作组件，位于 运营后台服务，封装 adminapplication 相关的校验、转换、持久化访问或运行时协作入口。
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
