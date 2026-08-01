package com.scott.payment.component.db.cache.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ManagedCacheInvalidationOutboxDO
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 跨 Admin 与 Merchant 写服务共享的永久缓存失效 Outbox 持久化模型
 * @status : create
 *
 * <p>继续映射历史表 {@code merchant_security_cache_invalidation_outbox}，保证升级前尚未完成的
 * INIT/FAILED 事件可以由新中继继续处理。表名只作为兼容约束，不限制事件的写入服务。</p>
 */
@Data
@TableName("merchant_security_cache_invalidation_outbox")
public class ManagedCacheInvalidationOutboxDO {

    /** Outbox 自增主键，不作为跨服务幂等标识。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 缓存失效事件唯一标识，用于发布重试和幂等查询。 */
    private String eventId;

    /** 需要失效的已登记 Spring Cache 名称。 */
    private String cacheName;

    /** 精确业务键，例如商户号或平台公开配置键。 */
    private String businessKey;

    /** pending 门禁租约令牌，属于内部控制数据，禁止写入日志。 */
    private String gateToken;

    /** 发布状态，仅允许 INIT、FAILED 或不可逆的 SENT。 */
    private String eventStatus;

    /** 发布失败累计次数，用于补偿监控。 */
    private Integer retryCount;

    /** 下一次允许补偿发布的时间；首次发布时允许为空。 */
    private LocalDateTime nextRetryTime;

    /** 首次成功完成缓存删除和门禁释放的时间。 */
    private LocalDateTime publishedTime;

    /** 最近一次失败原因，最长 512 字符且不得包含敏感资料。 */
    private String failureReason;

    /** 状态更新使用的乐观锁版本。 */
    private Integer version;

    /** 事件持久化时间，精度为毫秒。 */
    private LocalDateTime createTime;

    /** 最近一次状态更新或重试时间，精度为毫秒。 */
    private LocalDateTime updateTime;
}
