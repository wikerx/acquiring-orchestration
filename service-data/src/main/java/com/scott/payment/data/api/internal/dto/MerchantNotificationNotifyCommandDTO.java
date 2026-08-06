package com.scott.payment.data.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** service-data 单笔商户通知补偿命令。 */
@Data
public class MerchantNotificationNotifyCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 平台交易号，只用于业务定位，不用于解析分片时间。 */
    private String transactionId;

    /** 调用方从交易记录恢复的业务时间，用于精确定位季度分表。 */
    private LocalDateTime transactionDateTime;
}
