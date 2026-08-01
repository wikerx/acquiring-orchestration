package com.scott.payment.data.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationNotifyDueCommandDTO
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 到期商户通知补偿命令，按交易业务时间定位季度分表并限制单次处理数量
 * @status : create
 */
@Data
public class MerchantNotificationNotifyDueCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 交易业务时间，用于定位 transaction_merchant_notification 物理分表，不允许为空。 */
    private LocalDateTime transactionDateTime;

    /** 本次最大处理数量，允许为空并使用应用层默认值。 */
    private Integer limit;
}
