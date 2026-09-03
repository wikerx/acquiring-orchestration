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
 * @classname : PaymentCheckoutEventDO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 业务和页面事件实体。
 * @status : create
 */
@Data
@TableName("payment_checkout_event")
public class PaymentCheckoutEventDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台 Hosted Checkout 事件号。 */
    private String checkoutEventId;
    /** 关联会话号。 */
    private String checkoutSessionId;
    /** 关联支付尝试号；会话级事件可为空。 */
    private String checkoutAttemptId;
    /** 关联商户号。 */
    private String merchantId;
    /** 业务事件类型。 */
    private String eventType;
    /** 事件发生时的内部处理阶段。 */
    private String eventStage;
    /** 事件执行结果：SUCCESS、FAILED 或 IGNORED。 */
    private String eventResult;
    /** 事件执行前的会话状态。 */
    private String checkoutStatusBefore;
    /** 事件执行后的会话状态。 */
    private String checkoutStatusAfter;
    /** 事件执行前的支付尝试状态。 */
    private String attemptStatusBefore;
    /** 事件执行后的支付尝试状态。 */
    private String attemptStatusAfter;
    /** 关联支付动作号。 */
    private String operationId;
    /** 关联平台交易号。 */
    private String transactionId;
    /** 交易业务时间，用于分片交易定位。 */
    private LocalDateTime transactionDateTime;
    /** 调用链追踪号。 */
    private String traceId;
    /** 当前业务请求号，用于幂等关联。 */
    private String requestId;
    /** 客户端 IP 摘要。 */
    private String clientIpHash;
    /** User-Agent 摘要。 */
    private String userAgentHash;
    /** Origin 摘要。 */
    private String originHash;
    /** Referer 摘要。 */
    private String refererHash;
    /** 已脱敏且长度受控的事件负载 JSON。 */
    private String eventPayloadJson;
    /** 事件业务发生时间。 */
    private LocalDateTime eventTime;
    /** 数据库记录创建时间。 */
    private LocalDateTime createTime;
}
