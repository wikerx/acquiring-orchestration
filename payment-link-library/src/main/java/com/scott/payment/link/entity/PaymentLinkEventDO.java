package com.scott.payment.link.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 链接本地状态事务内的审计事实，不记录可付款 URL、客户邮箱或支付报文。 */
@Data
@TableName("payment_link_event")
public class PaymentLinkEventDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String linkId;
    private String eventType;
    private String actorAppCode;
    private String actorId;
    private String reason;
    private LocalDateTime createTime;
}
