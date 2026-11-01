package com.scott.payment.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 结算服务启动入口；服务无业务启停开关，启动后自动完成建批、计算、入账、交易投影和可靠消息发布。
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class SettlementApplication {

    /**
     * 启动结算服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SettlementApplication.class, args);
    }
}
