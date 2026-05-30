package com.scott.payment.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.scott.payment.component.db.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPlatformPayloadKeyDO
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 平台报文加密 RSA 密钥数据库实体
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_platform_payload_key")
public class OpenApiPlatformPayloadKeyDO extends BaseEntity {

    /**
     * 序列化版本号，用于保证实体在缓存、测试或序列化传输场景中的兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台 RSA 密钥编号，写入商户请求体 data 的受保护头 kid 字段。
     */
    private String platformKeyId;

    /**
     * 平台 RSA X.509 DER Base64 公钥，可下发给商户用于加密请求体。
     */
    private String publicKeyX509Base64;

    /**
     * 平台 RSA PKCS#8 DER Base64 私钥。
     * <p>
     * 当前测试环境按明文写入，生产环境必须进入 KMS、HSM 或加密配置，绝不能明文落库。
     */
    private String privateKeyPkcs8Base64;

    /**
     * 密钥算法，当前固定为 RSA-OAEP-256，表示 RSA-OAEP 使用 SHA-256 摘要。
     */
    private String algorithm;

    /**
     * RSA 密钥位数，当前建议不低于 2048，后续可平滑升级到 3072。
     */
    private Integer keySize;

    /**
     * 当前平台密钥是否启用。1 表示启用，0 表示停用。
     */
    private Integer enabled;

    /**
     * 逻辑删除标识。0 表示正常，1 表示删除，查询时必须过滤已删除记录。
     */
    private Integer deleted;
}
