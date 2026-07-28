package com.scott.payment.openapi.client.payout.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateClientRequestDTO
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : Payout Create Client Request DTO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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
