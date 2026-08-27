package com.scott.payment.settlement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementApplication
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算服务启动入口；服务无业务启停开关，启动后自动完成建批、计算、入账、交易投影和可靠消息发布。
 * @status : create
 */
@MapperScan("com.scott.payment.settlement.mapper")
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
