package com.global.payment.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.global.payment")
public class MerchantApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantApplication.class, args);
    }
}

