package com.scott.payment.risk.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 商户累计限额预占内部生命周期命令。
 */
@Data
public class MerchantLimitReservationCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台交易号，作为确认或取消全部预占记录的业务定位键。
     */
    @NotBlank(message = "transactionId is required")
    private String transactionId;

    /**
     * 风控评估流水号，仅用于链路关联；状态定位以 transactionId 为准。
     */
    private String riskRecordNo;

    /** 生命周期操作原因，仅用于审计和补偿说明，不参与幂等定位。 */
    private String reason;
}
