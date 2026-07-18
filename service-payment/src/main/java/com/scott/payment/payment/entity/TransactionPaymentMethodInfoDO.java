package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionPaymentMethodInfoDO
 * @date : 2026-07-15 14:30
 * @email : scott_x@163.com
 * @description : 交易支付工具摘要实体，位于 service-payment 持久化层，只保存支付方式、卡品牌、BIN、尾号、脱敏卡号和哈希等合规排查字段，不保存 CVV 或完整 PAN。
 * @status : create
 */
@Data
@TableName("transaction_payment_method_info")
public class TransactionPaymentMethodInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentInfoId;

    private String transactionId;

    private String operationId;

    private String paymentMethod;

    private String paymentBrand;

    private String cardBin;

    private String cardLast4;

    private String cardNumberMasked;

    private String cardholderNameMasked;

    private String expiryMonth;

    private String expiryYear;

    private String tokenId;

    private String walletType;

    private String paymentAccountHash;

    private String issuerCountry;

    private String fundingMethod;

    private String threeDsIndicator;

    private String cscResult;

    private String avsResult;

    private LocalDateTime transactionDateTime;

    private LocalDateTime transactionUtcTime;

    private String transactionTimeZone;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
