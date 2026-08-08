package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCardVaultStoreMessage
 * @date : 2026-08-08 18:00
 * @email : scott_x@163.com
 * @description : 收银台卡资料库写入消息，只传输由 service-data 公钥加密的无 CVV 信封和分片定位字段。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CheckoutCardVaultStoreMessage extends BaseMqMessage {

    private static final long serialVersionUID = 1L;

    /** AAD 协议版本，生产者和消费者必须保持一致。 */
    public static final String AAD_VERSION = "checkout-card-vault-v1";

    /** 交易所属商户号。 */
    private String merchantId;
    /** 平台交易号，也是卡资料与支付尝试的业务关联键。 */
    private String transactionId;
    /** 交易业务时间，用于季度分片精确路由。 */
    private LocalDateTime transactionDateTime;
    /** Hosted Checkout 支付尝试号。 */
    private String checkoutAttemptId;
    /** 传输信封算法，例如 RSA-OAEP-256+A256GCM。 */
    private String algorithm;
    /** service-data 传输公钥版本。 */
    private String keyId;
    /** RSA-OAEP-256 包裹的临时 AES 数据密钥。 */
    private String encryptedKey;
    /** AES-GCM 传输信封 IV。 */
    private String iv;
    /** 不含 CVV 的卡资料 AES-GCM 密文和认证标签。 */
    private String ciphertext;

    /**
     * 生成传输信封 AAD，防止密文被替换到另一商户、交易或消息。
     *
     * @return UTF-8 AAD 文本
     */
    public String transferAad() {
        return AAD_VERSION + "|" + merchantId + "|" + transactionId + "|" + getMessageId();
    }
}
