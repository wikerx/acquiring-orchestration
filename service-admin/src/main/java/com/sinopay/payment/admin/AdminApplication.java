package com.sinopay.payment.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminApplication
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Admin 服务启动类
 * @status : create
 */
@SpringBootApplication(scanBasePackages = "com.sinopay.payment")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}

