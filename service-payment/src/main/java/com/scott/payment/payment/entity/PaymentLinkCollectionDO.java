package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 开始收银台时建立的唯一收款关联；存在此记录不代表已经生成核心交易。 */
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
