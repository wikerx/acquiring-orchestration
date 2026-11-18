package com.scott.payment.openapi.config;

import lombok.Data;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** 支付链接匿名访问独立配额，不改变商户 OpenAPI 或普通收银台的限流配置。 */
@Data
@Component
@ConfigurationProperties(prefix="payment-link.browser")
public class PaymentLinkBrowserProperties {
    private int ipQueryLimit=120;
    private int linkQueryLimit=120;
    private int ipStartLimit=20;
    private int linkStartLimit=20;
    private Duration window=Duration.ofMinutes(1);
    @PostConstruct public void validate() {
        if(ipQueryLimit<1 || linkQueryLimit<1 || ipStartLimit<1 || linkStartLimit<1 || window==null
                || window.isNegative() || window.isZero() || window.compareTo(Duration.ofHours(1))>0) {
            throw new IllegalStateException("payment-link.browser limits and bounded window must be positive");
        }
    }
}
