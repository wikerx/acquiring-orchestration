package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 同一链接和收款实例只绑定一个可重试会话，数据库唯一性约束跨设备并发。 */
@Data
@TableName("payment_link_checkout_binding")
public class PaymentLinkCheckoutBindingDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String linkId;
    private String collectionId;
    private String checkoutSessionId;
    private LocalDateTime createTime;
}
