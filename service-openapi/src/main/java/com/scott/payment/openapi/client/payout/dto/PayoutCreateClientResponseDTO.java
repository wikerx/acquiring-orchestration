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
 * @description : PayoutCreateClientResponseDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
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
