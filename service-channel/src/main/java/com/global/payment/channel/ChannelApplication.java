package com.global.payment.channel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.global.payment")
public class ChannelApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChannelApplication.class, args);
    }
}

