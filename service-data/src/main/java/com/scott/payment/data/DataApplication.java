package com.scott.payment.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 异步数据服务启动入口，集中承载非资金事实日志消费、审计落库和商户通知发送
 */
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
