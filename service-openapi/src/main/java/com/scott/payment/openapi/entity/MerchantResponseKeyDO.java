package com.scott.payment.openapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.scott.payment.component.db.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantResponseKeyDO
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户响应加密公钥数据库实体
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_merchant_response_key")
public class MerchantResponseKeyDO extends BaseEntity {

    /**
     * 序列化版本号，用于保证实体在缓存、测试或序列化传输场景中的兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付框架颁发的商户号，用于关联 base_merchant_info.merchant_id。
     */
    private String merchantId;

    /**
     * 商户 RSA X.509 DER Base64 响应公钥。
     * <p>
     * 平台只保存商户响应公钥；响应私钥由商户自己保存，用于解密平台响应 data。
     */
    private String publicKeyX509Base64;

    /**
     * 响应加密算法，当前固定为 RSA-OAEP-256 + AES-256-GCM。
     */
    private String algorithm;

    /**
     * RSA 密钥位数，当前建议不低于 2048。
     */
    private Integer keySize;

    /**
     * 当前响应公钥是否启用。1 表示启用，0 表示停用。
     */
    private Integer enabled;

    /**
     * 逻辑删除标识。0 表示正常，1 表示删除，查询时必须过滤已删除记录。
     */
    private Integer deleted;
}
