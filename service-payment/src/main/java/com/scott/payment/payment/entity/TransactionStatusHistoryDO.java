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
 * @classname : TransactionStatusHistoryDO
 * @date : 2026-07-14 17:35
 * @email : scott_x@163.com
 * @description : 交易状态历史实体，位于 service-payment 持久化层，记录订单和动作状态每一次流转，用于审计和详情页时间线。
 * @status : create
 */
@Data
@TableName("transaction_status_history")
public class TransactionStatusHistoryDO implements Serializable {

    /**
     * 序列化版本号，用于本地缓存、测试和后续补偿场景的对象兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 物理表主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 状态历史 ID。
     */
    private String statusHistoryId;

    /**
     * 平台当前交易唯一标识。
     */
    private String transactionId;

    /**
     * 平台内部生命周期关联标识。
     */
    private String operationId;

    /**
     * 状态对象，如 ORDER、OPERATION、FINANCE、NOTIFICATION。
     */
    private String statusObject;

    /**
     * 流转前状态，首次创建可为空。
     */
    private String fromStatus;

    /**
     * 流转后状态，对齐字典 transaction_status。
     */
    private String toStatus;

    /**
     * 触发类型，如 API、CHANNEL_RESPONSE、CHANNEL_CALLBACK、JOB。
     */
    private String triggerType;

    /**
     * 触发对象 ID，如 request_id、callback_id、job_log_id。
     */
    private String triggerId;

    /**
     * 流转结果，如 SUCCESS、REJECTED、IGNORED。
     */
    private String transitionResult;

    /**
     * 流转失败或忽略原因摘要。
     */
    private String failReason;

    /**
     * 流转前版本号。
     */
    private Integer versionBefore;

    /**
     * 流转后版本号。
     */
    private Integer versionAfter;

    /**
     * 状态流转时间。
     */
    private LocalDateTime statusTime;

    /**
     * 交易业务时间，所有交易分表统一字段。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易业务时间对应 UTC 时间。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时间所属 IANA 时区。
     */
    private String transactionTimeZone;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createTime;
}
