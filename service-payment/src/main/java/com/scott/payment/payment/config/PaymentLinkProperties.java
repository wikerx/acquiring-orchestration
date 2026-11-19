package com.scott.payment.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 支付入口与管理端读取同一套链接专用配置，保留已有配置前缀。 */
@Component
@ConfigurationProperties(prefix = "payment.link")
public class PaymentLinkProperties extends com.scott.payment.link.config.PaymentLinkSettings { }
