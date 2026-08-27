package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingPaymentMethodInfoDO
 * @date : 2026-08-26 10:30
 * @email : scott_x@163.com
 * @description : 清分费用维度使用的支付工具非敏感摘要，只读取支付方式、品牌与3DS调用标识。
 * @status : create
 */
@Data
@TableName("transaction_payment_method_info")
public class ClearingPaymentMethodInfoDO {
    private Long id;
    private String transactionId;
    private String operationId;
    private String paymentMethod;
    private String paymentBrand;
    private String threeDsIndicator;
    private LocalDateTime transactionDateTime;
}
