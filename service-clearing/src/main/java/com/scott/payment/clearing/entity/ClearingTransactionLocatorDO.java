package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionLocatorDO
 * @date : 2026-08-26 10:30
 * @email : scott_x@163.com
 * @description : 清分服务对非分表 transaction_locator 的只读投影，用于恢复当前、源动作和根主单的真实分片时间。
 * @status : create
 */
@Data
@TableName("transaction_locator")
public class ClearingTransactionLocatorDO {
    private Long id;
    private String transactionId;
    private String operationId;
    private String rootTransactionId;
    private String merchantId;
    private String merchantOrderNo;
    private String transactionType;
    private LocalDateTime transactionDateTime;
    private LocalDateTime rootTransactionDateTime;
}
