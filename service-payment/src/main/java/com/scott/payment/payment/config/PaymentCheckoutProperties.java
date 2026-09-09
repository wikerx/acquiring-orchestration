package com.scott.payment.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutProperties
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 运行配置。
 * @status : create
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

    /** 浏览器卡数据加密、公钥轮换和 nonce 防重放配置。 */
    private CardEncryption cardEncryption = new CardEncryption();

    /** 不含 CVV 的卡资料异步归档配置，默认关闭。 */
    private CardVault cardVault = new CardVault();

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardEncryption
     * @date : 2026-09-02 08:03
     * @email : scott_x@163.com
     * @description : 卡encryption嵌套数据模型，定义所属聚合内固定的字段集合和传递边界。
     * @status : create
     */
    @Data
    public static class CardEncryption {
        /** 当前 RSA 密钥版本，浏览器信封必须原样回传。 */
        private String keyId = "checkout-card-v1";
        /** X.509 DER Base64 RSA 公钥；UAT/生产必须由 Secret 或 KMS 注入。 */
        private String publicKeyX509Base64;
        /** PKCS#8 DER Base64 RSA 私钥；只允许 service-payment 读取。 */
        private String privateKeyPkcs8Base64;
        /** 本地开发是否允许生成进程级临时密钥；UAT/生产必须为 false。 */
        private boolean allowEphemeralKey;
        /** nonce 有效期秒数，覆盖正常填写和提交窗口。 */
        private long nonceTtlSeconds = 900;
        /** Redis 不可用时是否失败关闭；UAT/生产必须为 true。 */
        private boolean replayStoreRequired = true;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardVault
     * @date : 2026-09-02 08:03
     * @email : scott_x@163.com
     * @description : 卡vault嵌套数据模型，定义所属聚合内固定的字段集合和传递边界。
     * @status : create
     */
    @Data
    public static class CardVault {
        /** 是否向 service-data 发送卡资料密文；表、Topic、分片规则和密钥就绪后才能开启。 */
        private boolean enabled;
        /** service-data 卡资料传输 RSA 公钥版本。 */
        private String keyId = "checkout-card-vault-v1";
        /** service-data 卡资料传输 X.509 DER Base64 RSA 公钥。 */
        private String publicKeyX509Base64;
    }
}
