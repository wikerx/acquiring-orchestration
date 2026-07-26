package com.scott.payment.payout.api.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateCommandDTO
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : PayoutCreateCommandDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 代付服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class PayoutCreateCommandDTO {

    /**
     * 商户号。
     */
    @NotBlank(message = "merchantId is required")
    private String merchantId;

    /**
     * 商户代付单号。
     */
    @NotBlank(message = "merchantOrderNo is required")
    private String merchantOrderNo;

    /**
     * 代付金额。
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    /**
     * 币种。
     */
    @NotBlank(message = "currency is required")
    private String currency;

    /**
     * 请求时间。
     */
    private LocalDateTime transactionDateTime;
}
