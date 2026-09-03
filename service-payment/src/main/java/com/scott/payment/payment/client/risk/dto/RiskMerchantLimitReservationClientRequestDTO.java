package com.scott.payment.payment.client.risk.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskMerchantLimitReservationClientRequestDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : service-risk 商户累计限额预占补偿请求。
 * @status : create
 */
@Data
public class RiskMerchantLimitReservationClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 触发限额预占的支付交易号，也是补偿幂等关联键。 */
    private String transactionId;

    /** 风控评估记录号，用于定位原预占记录。 */
    private String riskRecordNo;

    /** 受控补偿原因编码，不应写入异常堆栈或敏感报文。 */
    private String reason;
}
