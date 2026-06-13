package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户 OpenAPI JWT 签名密钥。
 */
@Data
@TableName("base_merchant_jwt_key")
public class BaseMerchantJwtKeyDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private String keyVersion;

    private String merchantKey;

    private String algorithm;

    private Long expiresSeconds;

    private Integer enabled;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private Integer deleted;
}
