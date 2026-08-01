package com.scott.payment.data;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataApplication
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : 异步数据服务启动入口，集中承载非资金事实日志消费、审计落库和商户通知发送
 * @status : create
 */
@MapperScan("com.scott.payment.data.mapper")
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class DataApplication {

    /**
     * 启动异步数据服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DataApplication.class, args);
    }
}
