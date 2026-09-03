package com.scott.payment.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskCacheInvalidationOutboxDO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控规则缓存失效事件持久化模型。
 * @status : create
 */
@Data
@TableName("risk_cache_invalidation_outbox")
public class RiskCacheInvalidationOutboxDO {

    /** Outbox 自增主键，不作为规则发布幂等标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 风控规则缓存失效事件唯一标识。 */
    private String eventId;

    /** generation 存储使用的规则缓存命名空间。 */
    private String namespace;

    /** generation 发布租约的不透明令牌，不得写入普通日志或对外响应。 */
    private String publicationToken;

    /** 本次事务预留的新规则 generation，提交后才允许发布。 */
    private String generation;

    /** 发布状态，取值为 INIT、FAILED 或不可逆的 SENT。 */
    private String eventStatus;

    /** generation 发布失败累计次数，用于补偿监控。 */
    private Integer retryCount;

    /** 下一次允许补偿发布的时间；首次发布时允许为空。 */
    private LocalDateTime nextRetryTime;

    /** generation 成功切换并完成事件确认的时间。 */
    private LocalDateTime publishedTime;

    /** 最近一次失败原因，长度受控且不得包含规则敏感载荷。 */
    private String failureReason;

    /** Outbox 状态更新使用的乐观锁版本。 */
    private Integer version;

    /** 事件持久化时间，精度为毫秒。 */
    private LocalDateTime createTime;

    /** 最近一次状态更新或重试时间，精度为毫秒。 */
    private LocalDateTime updateTime;
}
