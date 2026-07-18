package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantApiInteractionLogDO
 * @date : 2026-07-15 23:20
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 交互日志实体，位于 service-payment 持久化层，保存商户请求与平台响应的脱敏明文、密文摘要和处理结果，禁止保存完整卡号、CVV、JWT 或密钥。
 * @status : create
 */
@Data
@TableName("transaction_merchant_api_interaction_log")
public class TransactionMerchantApiInteractionLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物理表主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户 API 交互日志唯一标识。
     */
    private String apiLogId;

    /**
     * 商户请求唯一号，当前通常等于 orderInfo.orderId。
     */
    private String requestId;

    /**
     * 平台当前交易唯一标识。
     */
    private String transactionId;

    /**
     * 平台内部生命周期关联标识，不返回商户。
     */
    private String operationId;

    /**
     * 平台商户号。
     */
    private String merchantId;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识。
     */
    private String merchantOrderId;

    /**
     * 商户 API 动作，如 AUTHORIZATION、CAPTURE、REFUND。
     */
    private String apiOperation;

    /**
     * 商户请求路径。
     */
    private String requestPath;

    /**
     * 商户请求进入支付核心的时间。
     */
    private LocalDateTime requestTime;

    /**
     * 请求处理结果，如 SUCCESS、FAILED、PROCESSING。
     */
    private String requestResult;

    /**
     * 商户请求密文摘要，使用平台安全指纹，不保存完整密文。
     */
    private String requestCipherDigest;

    /**
     * 商户请求密文掩码，只保留极短首尾片段用于人工核对。
     */
    private String requestCipherMasked;

    /**
     * 商户请求脱敏明文 JSON。
     */
    private String requestPlainJsonMasked;

    /**
     * 平台响应生成时间。
     */
    private LocalDateTime responseTime;

    /**
     * 平台响应处理结果。
     */
    private String responseResult;

    /**
     * 商户侧可见响应码。
     */
    private String merchantResponseCode;

    /**
     * 商户侧可见响应描述。
     */
    private String merchantResponseMessage;

    /**
     * 平台响应商户的脱敏明文 JSON。
     */
    private String responsePlainJsonMasked;

    /**
     * 平台响应密文摘要；当前响应加密发生在 OpenAPI 响应切面，支付核心阶段可为空。
     */
    private String responseCipherDigest;

    /**
     * 平台响应密文掩码；当前响应加密发生在 OpenAPI 响应切面，支付核心阶段可为空。
     */
    private String responseCipherMasked;

    /**
     * OpenAPI 到支付核心处理耗时，单位毫秒。
     */
    private Integer durationMillis;

    /**
     * 链路追踪 ID，可为空。
     */
    private String traceId;

    /**
     * 交易业务时间，用于分表路由。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易业务时间对应 UTC 时间。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时区。
     */
    private String transactionTimeZone;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
