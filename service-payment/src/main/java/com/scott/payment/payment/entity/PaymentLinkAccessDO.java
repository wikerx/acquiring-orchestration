package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 可重建签名的不可变上下文；数据库只保留完整访问凭据的不可逆摘要。 */
@Data
@TableName("payment_link_access")
public class PaymentLinkAccessDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String linkId;
    private String keyVersion;
    private String tokenHash;
    private LocalDateTime createTime;
}
