package com.scott.payment.clearing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionAbnormalEventDO
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分服务对既有 transaction_abnormal_event 逻辑表的最小持久化视图。
 * @status : update
 */
@Data
public class ClearingTransactionAbnormalEventDO {
    /** 异常案件业务号。 */
    private String abnormalEventId;
    /** 关联动作交易号。 */
    private String transactionId;
    /** 关联动作操作号。 */
    private String operationId;
    /** 清分异常类型稳定编码。 */
    private String abnormalType;
    /** 运营处置优先级。 */
    private String abnormalLevel;
    /** 案件状态；重复异常可重新打开。 */
    private String eventStatus;
    /** 来源记录类型，清分固定为财务状态。 */
    private String sourceRecordType;
    /** 来源财务状态号与清分修订组合。 */
    private String sourceRecordId;
    /** 已去换行并限制长度的非敏感异常摘要。 */
    private String abnormalDescription;
    /** 原始引用 JSON；清分异常禁止写入，保持为空。 */
    private String rawReferenceJson;
    /** 首次发现 UTC 时间。 */
    private LocalDateTime firstSeenTime;
    /** 关联动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 关联动作 UTC 业务时间。 */
    private LocalDateTime transactionUtcTime;
    /** 关联动作业务 IANA 时区。 */
    private String transactionTimeZone;
    /** financeStateId、修订、类型和失败码的 SHA-256 去重键。 */
    private String deduplicationKey;
    /** 平台商户号。 */
    private String merchantId;
    /** 商户订单号，仅用于运营定位。 */
    private String merchantOrderNo;
    /** 关联源交易号；无源动作时为空。 */
    private String sourceTransactionId;
    /** 平台统一交易类型。 */
    private String transactionType;
    /** 清分稳定失败码，不保存异常堆栈。 */
    private String platformStatus;
    /** 渠道匹配结果；清分案件固定为 NOT_APPLICABLE。 */
    private String channelMatchResult;
    /** 检测来源，清分固定为 CLEARING_SERVICE。 */
    private String detectSource;
    /** 最近一次发现 UTC 时间。 */
    private LocalDateTime lastSeenTime;
    /** 相同去重键累计出现次数。 */
    private Integer occurrenceCount;
    /** 是否需要商户通知；清分内部案件固定为 0。 */
    private Integer merchantNotifyRequired;
    /** 案件乐观锁版本。 */
    private Integer version;
    /** 逻辑删除标识。 */
    private Integer deleted;
    /** 创建 UTC 时间。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间。 */
    private LocalDateTime updateTime;
}
