package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一条链接唯一付款目标，金额和交易账本仍由链接快照及现有核心所有。 */
@Data
@TableName("payment_link_collection")
public class PaymentLinkCollectionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String collectionId;
    private String linkId;
    private String merchantId;
    private String merchantOrderNo;
    private LocalDateTime submissionDeadline;
    private LocalDateTime createTime;
}
