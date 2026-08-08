package com.scott.payment.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataCardVaultProperties
 * @date : 2026-08-08 18:00
 * @email : scott_x@163.com
 * @description : service-data 卡资料库密钥版本和消费开关配置，密钥值只能由 Secret 或 KMS 注入。
 * @status : create
 */
@Data
@Component
@ConfigurationProperties(prefix = "data.card-vault")
public class DataCardVaultProperties {

    /** 表、分片规则、Topic 和密钥全部就绪后才允许开启。 */
    private boolean enabled;
    /** 与 service-payment 公钥匹配的传输密钥版本。 */
    private String transferKeyId = "checkout-card-vault-v1";
    /** PKCS#8 DER Base64 RSA 传输私钥，只允许 service-data 读取。 */
    private String transferPrivateKeyPkcs8Base64;
    /** PAN HMAC pepper 版本，用于后续轮换和查询兼容。 */
    private String panHmacKeyVersion = "v1";
    /** PAN HMAC-SHA256 secret pepper，至少 32 字节。 */
    private String panHmacPepper;
    /** 包裹数据密钥的 KEK 版本。 */
    private String kekVersion = "v1";
    /** 32 字节 KEK 的标准 Base64 值，不得存入卡资料表。 */
    private String kekBase64;
}
