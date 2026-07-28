package com.scott.payment.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hosted Checkout 运行配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.checkout")
public class PaymentCheckoutProperties {

    /**
     * 本地开发默认 pepper；UAT/生产必须通过配置中心或环境变量覆盖。
     */
    private String tokenPepper = "dev-hosted-checkout-token-pepper-change-me";

    /**
     * token 摘要密钥版本，便于后续 pepper 轮换。
     */
    private String tokenKeyVersion = "dev-v1";

    /**
     * opaque token 随机字节长度。
     */
    private int opaqueTokenBytes = 32;

    /**
     * URL cover 随机字节长度。
     */
    private int coverBytes = 9;

    /**
     * 默认会话有效分钟数。
     */
    private int defaultExpireMinutes = 30;

    /**
     * 默认最大支付尝试次数。
     */
    private int defaultMaxAttemptCount = 3;

    /**
     * 前端轮询基础间隔秒数。
     */
    private int pollingIntervalSeconds = 3;

    /**
     * 前端轮询最大间隔秒数。
     */
    private int maxPollingIntervalSeconds = 15;

    /**
     * 默认交易动作，V1 先以一步支付为主。
     */
    private String defaultPaymentAction = "PAYMENT";

    /**
     * 收银台集成类型。
     */
    private String integrationType = "HOSTED_CHECKOUT";
}
