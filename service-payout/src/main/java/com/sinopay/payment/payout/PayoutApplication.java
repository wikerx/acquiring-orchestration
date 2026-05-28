package com.sinopay.payment.payout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sinopay.payment")
public class PayoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayoutApplication.class, args);
    }
}

