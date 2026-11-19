package com.scott.payment.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 未配置时仅关闭链接写操作，不影响既有管理查询。 */
@Component
@ConfigurationProperties(prefix = "payment.link")
public class MerchantPaymentLinkProperties extends com.scott.payment.link.config.PaymentLinkSettings { }
