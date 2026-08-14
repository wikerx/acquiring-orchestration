package com.scott.payment.data.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationTaskDO
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知执行投影，只映射抢占、HTTP 投递、重试和审计所需字段，不作为交易事实实体
 * @status : create
 */
@Data
public class DataMerchantNotificationTaskDO {

    /** 通知任务数据库主键，不允许为空。 */
    private Long id;

    /** 稳定通知 ID，重复投递必须保持不变，供商户侧实现幂等。 */
    private String notifyId;

    /** 平台交易 ID，不允许为空。 */
    private String transactionId;

    /** 平台交易生命周期操作 ID，允许为空。 */
    private String operationId;

    /** 平台商户号，不允许为空。 */
    private String merchantId;

    /** 商户订单号，允许为空，仅用于通知追踪。 */
    private String merchantOrderNo;

    /** 商户回调地址明文，只允许用于 HTTP 投递且禁止完整写入日志或 Redis。 */
    private String callbackUrl;

    /** 正式商户回调业务载荷明文 JSON，发送前仍通过 OpenAPI 响应公钥加密。 */
    private String payloadJson;

    /** 实际回调 URL 的 SHA-256 摘要，用于关联审计。 */
    private String targetUrlHash;

    /** 已移除查询参数值的回调 URL，只允许用于日志和管理端展示。 */
    private String targetUrlMasked;

    /** 已按商户可见字段构造并完成敏感数据脱敏的回调 JSON。 */
    private String payloadJsonMasked;

    /** 回调签名类型；当前为空时表示沿用既有无签名协议。 */
    private String signType;

    /** 已开始执行的通知次数，从零递增。 */
    private Integer lastAttemptNo;

    /** 最大通知次数，必须大于零。 */
    private Integer maxRetryCount;

    /** 交易业务时间，用于定位通知任务和日志的季度分表。 */
    private LocalDateTime transactionDateTime;

    /** 乐观锁版本号，用于多实例通知任务抢占和终态 CAS。 */
    private Integer version;
}
