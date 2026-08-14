package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionProductItemDO
 * @date : 2026-08-14 12:43
 * @email : scott_x@163.com
 * @description : 商户商品明细快照实体，每行保存名称、数量和行金额，不参与重新计算支付本金。
 * @status : create
 */
@Data
@TableName("transaction_product_item")
public class TransactionProductItemDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productItemId;
    private String transactionId;
    private String operationId;
    private String merchantId;
    private String merchantOrderNo;
    private Integer itemSequence;
    private String productName;
    private BigDecimal quantity;
    private String itemCurrency;
    private BigDecimal itemAmount;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
