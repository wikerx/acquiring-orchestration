package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户响应加密公钥。
 */
@Data
@TableName("base_merchant_response_key")
public class BaseMerchantResponseKeyDO {

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
