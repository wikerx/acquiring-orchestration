package com.scott.payment.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.scott.payment.component.db.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantJwtKeyDO
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户 JWT 签名密钥数据库实体
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_merchant_jwt_key")
public class OpenApiMerchantJwtKeyDO extends BaseEntity {

    /**
     * 序列化版本号，用于保证实体在缓存、测试或序列化传输场景中的兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付框架颁发的商户号，用于关联 base_merchant_info.merchant_id。
     */
    private String merchantId;

    /**
     * 商户 JWT 密钥版本号。密钥轮换时同一个 merchantId 可保留多个版本。
     */
    private String keyVersion;

    /**
     * 商户 JWT HS256 签名密钥。
     * <p>
     * 当前测试环境按明文写入，生产环境必须改为 KMS 或密文字段，并在服务侧解密后再参与验签。
     */
    private String merchantKey;

    /**
     * JWT 签名算法，当前固定为 HS256，便于后续审计和兼容性校验。
     */
    private String algorithm;

    /**
     * JWT 最大有效期，单位秒。当前开放接口要求不超过 180 秒。
     */
    private Long expiresSeconds;

    /**
     * 当前密钥是否启用。1 表示启用，0 表示停用。
     */
    private Integer enabled;

    /**
     * 密钥生效时间，用于密钥预发布和灰度切换。
     */
    private LocalDateTime effectiveTime;

    /**
     * 密钥失效时间，允许为空；为空表示未配置固定失效时间。
     */
    private LocalDateTime expireTime;

    /**
     * 逻辑删除标识。0 表示正常，1 表示删除，查询时必须过滤已删除记录。
     */
    private Integer deleted;
}
