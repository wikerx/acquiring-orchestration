package com.scott.payment.merchant.entity.paymentlink;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 每个发送请求持久化占位；只保存收件人摘要，不保存分享凭据或原始收件人。 */
@Data
@TableName("merchant_payment_link_delivery_request")
public class PaymentLinkDeliveryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String merchantId;
    private String linkId;
    private String fingerprint;
    private String recipientHash;
    private String recipientMasked;
    private String locale;
    private String bizNo;
    private Long emailRecordId;
    private String createdBy;
    private LocalDateTime createTime;
}
