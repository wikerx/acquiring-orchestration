package com.scott.payment.clearing;

import com.scott.payment.clearing.config.ClearingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 交易清分服务启动入口；启动时校验内部HMAC与28表交易拓扑，校验通过后自动运行消费者和指标调度。
 */
@EnableScheduling
@EnableConfigurationProperties(ClearingProperties.class)
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class ClearingApplication {

    /**
     * 启动交易清分服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ClearingApplication.class, args);
    }
}
