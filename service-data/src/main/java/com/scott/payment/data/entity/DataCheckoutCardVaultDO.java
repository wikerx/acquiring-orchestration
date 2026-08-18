package com.scott.payment.data.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataCheckoutCardVaultDO
 * @date : 2026-08-08 18:00
 * @email : scott_x@163.com
 * @description : service-data 卡资料库持久化对象，仅保存 HMAC、显示摘要、字段密文和被 KEK 包裹的 DEK。
 * @status : create
 */
@Data
public class DataCheckoutCardVaultDO {

    /** 数据库自增主键。 */
    private Long id;
    /** 卡资料库记录号。 */
    private String vaultRecordId;
    /** MQ 消息号，承担重复消费最终幂等。 */
    private String messageId;
    /** 商户号。 */
    private String merchantId;
    /** Hosted Checkout 支付尝试号。 */
    private String checkoutAttemptId;
    /** 平台交易号。 */
    private String transactionId;
    /** 交易时间和季度分片键。 */
    private LocalDateTime transactionDateTime;
    /** 卡品牌。 */
    private String cardBrand;
    /** 前六位 BIN，仅供路由和排查。 */
    private String cardBin;
    /** 卡号后四位，仅供安全展示。 */
    private String cardLast4;
    /** 带 secret pepper 的 PAN HMAC-SHA256。 */
    private String panHmac;
    /** PAN HMAC 密钥版本。 */
    private String panHmacKeyVersion;
    /** PAN AES-GCM 密文。 */
    private String panCiphertext;
    /** PAN AES-GCM IV。 */
    private String panIv;
    /** PAN AES-GCM 认证标签。 */
    private String panAuthTag;
    /** 有效期 AES-GCM 密文。 */
    private String expirationCiphertext;
    /** 有效期 AES-GCM IV。 */
    private String expirationIv;
    /** 有效期 AES-GCM 认证标签。 */
    private String expirationAuthTag;
    /** 持卡人姓名 AES-GCM 密文。 */
    private String cardholderNameCiphertext;
    /** 持卡人姓名 AES-GCM IV。 */
    private String cardholderNameIv;
    /** 持卡人姓名 AES-GCM 认证标签。 */
    private String cardholderNameAuthTag;
    /** 被 KEK 包裹的随机 DEK 密文。 */
    private String wrappedDekCiphertext;
    /** 包裹 DEK 使用的 AES-GCM IV。 */
    private String wrappedDekIv;
    /** 包裹 DEK 使用的 AES-GCM 认证标签。 */
    private String wrappedDekAuthTag;
    /** KEK 版本，不存储 KEK 明文。 */
    private String kekVersion;
    /** 乐观锁版本。 */
    private Integer version;
    /** 逻辑删除标识。 */
    private Integer deleted;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
