package com.scott.payment.openapi.client.payout.dto;

import lombok.Data;

import java.io.Serializable;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutCreateClientResponseDTO
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : Payout Create Client Response DTO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
public class PayoutCreateClientResponseDTO implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台代付单号。
     */
    private String payoutOrderNo;

    /**
     * 商户代付单号。
     */
    private String merchantOrderNo;

    /**
     * 当前状态。
     */
    private String status;
}
