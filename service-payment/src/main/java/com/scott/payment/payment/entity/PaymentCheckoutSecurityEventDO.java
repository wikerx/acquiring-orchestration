package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutSecurityEventDO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 安全事件实体。
 * @status : create
 */
@Data
@TableName("payment_checkout_security_event")
public class PaymentCheckoutSecurityEventDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台安全事件号。 */
    private String securityEventId;
    /** 关联 Hosted Checkout 会话号。 */
    private String checkoutSessionId;
    /** 关联支付尝试号；会话打开事件可为空。 */
    private String checkoutAttemptId;
    /** 关联商户号。 */
    private String merchantId;
    /** 不透明访问令牌摘要，绝不保存令牌明文。 */
    private String tokenHash;
    /** 安全事件类型。 */
    private String securityEventType;
    /** 安全决策：ALLOW、BLOCK、CHALLENGE 或 LOG_ONLY。 */
    private String securityDecision;
    /** 阻断原因编码；未阻断时可为空。 */
    private String blockReasonCode;
    /** 当前请求对应的 HTTP 状态码。 */
    private Integer httpStatus;
    /** 当前请求的 HTTP 方法。 */
    private String requestMethod;
    /** 请求路径摘要，避免持久化非受控查询参数。 */
    private String requestPathHash;
    /** 客户端 IP 摘要。 */
    private String clientIpHash;
    /** 客户端 IP 解析出的国家或地区代码。 */
    private String clientIpCountry;
    /** User-Agent 摘要。 */
    private String userAgentHash;
    /** 设备标识摘要。 */
    private String deviceIdHash;
    /** Origin 摘要。 */
    private String originHash;
    /** Referer 摘要。 */
    private String refererHash;
    /** 风险评分，仅用于安全决策证据，不代表资金金额。 */
    private BigDecimal riskScore;
    /** 已脱敏且长度受控的安全证据 JSON。 */
    private String evidenceJson;
    /** 调用链追踪号。 */
    private String traceId;
    /** 安全事件业务发生时间。 */
    private LocalDateTime eventTime;
    /** 数据库记录创建时间。 */
    private LocalDateTime createTime;
}
