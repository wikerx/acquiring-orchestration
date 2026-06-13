package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台按商户维度保存的 OpenAPI 请求体 RSA 密钥。
 */
@Data
@TableName("base_platform_payload_key")
public class BasePlatformPayloadKeyDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private String publicKeyX509Base64;

    private String privateKeyPkcs8Base64;

    private String algorithm;

    private Integer keySize;

    private Integer enabled;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private Integer deleted;
}
