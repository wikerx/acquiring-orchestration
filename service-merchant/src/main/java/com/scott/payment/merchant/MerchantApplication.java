package com.scott.payment.merchant;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商户服务启动类。
 */
@Import(MerchantPaymentGovernanceConfiguration.class)
@SpringBootApplication(scanBasePackages = "com.scott.payment")
public class MerchantApplication {

    /**
     * 启动商户服务。
     *
     * @param args JVM 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MerchantApplication.class, args);
    }
}
