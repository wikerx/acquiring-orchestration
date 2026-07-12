package com.scott.payment.openapi.client.payout.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateClientRequestDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIPayout Create Client Request 数据传输对象，位于 service-openapi 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class PayoutCreateClientRequestDTO implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户代付单号。
     */
    private String merchantOrderNo;

    /**
     * 代付金额。
     */
    private BigDecimal amount;

    /**
     * 币种。
     */
    private String currency;

    /**
     * 业务时间。
     */
    private LocalDateTime transactionDateTime;

    /**
     * OpenAPI 收到的密文请求体指纹。
     */
    private String requestFingerprint;
}
