package com.global.payment.payout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.global.payment")
public class PayoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayoutApplication.class, args);
    }
}

