package com.sinopay.payment.channel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelApplication
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Channel 服务启动类
 * @status : create
 */
@SpringBootApplication(scanBasePackages = "com.sinopay.payment")
public class ChannelApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChannelApplication.class, args);
    }
}

