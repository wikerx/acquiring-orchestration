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
    /** 支付工具记录主键。 */
    private Long id;
    /** 动作交易号。 */
    private String transactionId;
    /** 动作操作号。 */
    private String operationId;
    /** 非敏感支付方式编码，例如 CARD。 */
    private String paymentMethod;
    /** 非敏感支付品牌编码，例如 VISA；未知时为空。 */
    private String paymentBrand;
    /** 是否实际调用 3DS 的稳定标识，不包含认证报文。 */
    private String threeDsIndicator;
    /** 支付工具记录所属动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
}
