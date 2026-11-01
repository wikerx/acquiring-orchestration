package com.scott.payment.payout.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务间创建代付交易的请求模型。
 *
 * <p>未知字段由服务端忽略，以兼容调用方先行发布的可选协议字段。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;
}
