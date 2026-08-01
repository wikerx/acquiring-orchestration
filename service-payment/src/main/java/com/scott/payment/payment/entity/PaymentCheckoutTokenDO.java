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

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务端访问令牌记录号，不是付款人持有的令牌明文。 */
    private String checkoutTokenId;
    /** 令牌绑定的 Hosted Checkout 会话号。 */
    private String checkoutSessionId;
    /** 令牌所属商户号。 */
    private String merchantId;
    /** 不透明访问令牌的不可逆摘要。 */
    private String tokenHash;
    /** 令牌摘要算法标识。 */
    private String tokenHashAlg;
    /** 计算摘要时使用的密钥版本，仅用于轮换定位。 */
    private String tokenKeyVersion;
    /** 令牌状态：ACTIVE、REVOKED 或 EXPIRED。 */
    private String tokenStatus;
    /** 令牌签发原因编码。 */
    private String issueReason;
    /** 令牌失效时间。 */
    private LocalDateTime expireTime;
    /** 令牌首次成功使用时间。 */
    private LocalDateTime firstUsedTime;
    /** 令牌最近一次成功使用时间。 */
    private LocalDateTime lastUsedTime;
    /** 令牌累计成功使用次数。 */
    private Integer useCount;
    /** 最近访问客户端 IP 摘要。 */
    private String lastClientIpHash;
    /** 最近访问 User-Agent 摘要。 */
    private String lastUserAgentHash;
    /** 令牌撤销时间。 */
    private LocalDateTime revokedTime;
    /** 令牌撤销原因编码。 */
    private String revokeReasonCode;
    /** 乐观锁版本号。 */
    private Integer version;
    /** 逻辑删除标识。 */
    private Integer deleted;
    /** 数据库记录创建时间。 */
    private LocalDateTime createTime;
    /** 数据库记录最近更新时间。 */
    private LocalDateTime updateTime;
}
