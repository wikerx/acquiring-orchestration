package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Hosted Checkout URL token 摘要实体。
 */
@Data
@TableName("payment_checkout_token")
public class PaymentCheckoutTokenDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String checkoutTokenId;
    private String checkoutSessionId;
    private String merchantId;
    private String tokenHash;
    private String tokenHashAlg;
    private String tokenKeyVersion;
    private String tokenStatus;
    private String issueReason;
    private LocalDateTime expireTime;
    private LocalDateTime firstUsedTime;
    private LocalDateTime lastUsedTime;
    private Integer useCount;
    private String lastClientIpHash;
    private String lastUserAgentHash;
    private LocalDateTime revokedTime;
    private String revokeReasonCode;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
